package com.youdash.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.realtime.UserOrderEventDTO;
import com.youdash.exception.BadRequestException;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.model.PaymentType;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.realtime.RiderActiveOrderTopicPublisher;
import com.youdash.realtime.UserActiveOrderTopicPublisher;
import com.youdash.realtime.AdminOrderTopicPublisher;
import com.youdash.util.DeliveryOtpGenerator;
import com.youdash.util.TransactionAfterCommit;
import com.youdash.service.DispatchService;
import com.youdash.service.NotificationService;
import com.youdash.service.OrderStatusTransitionGuard;
import com.youdash.service.OrderTimelineService;
import com.youdash.service.RiderOrderService;

@Service
public class RiderOrderServiceImpl implements RiderOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderServiceImpl orderServiceImpl; // reuse toOrderDtoForRider (strips GST/platform breakdown)

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RiderActiveOrderTopicPublisher riderActiveOrderTopicPublisher;

    @Autowired
    private UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Autowired
    private AdminOrderTopicPublisher adminOrderTopicPublisher;

    @Autowired
    private OrderTimelineService orderTimelineService;

    @Autowired
    private OrderStatusTransitionGuard orderStatusTransitionGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> accept(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (riderId == null || orderId == null) {
            response.setMessage("riderId and orderId are required");
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
            return response;
        }

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getServiceMode() != ServiceMode.INCITY) {
            throw new RuntimeException("Only INCITY orders can be accepted by rider");
        }
        if (order.getStatus() != OrderStatus.SEARCHING_RIDER) {
            throw new RuntimeException("Order not available for acceptance");
        }
        if (order.getSearchExpiresAt() != null && order.getSearchExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Order request expired");
        }
        if (!dispatchService.wasRiderDispatched(orderId, riderId)) {
            throw new RuntimeException("Rider was not dispatched for this order");
        }

        // 1) Lock rider immediately (reserve).
        int reserved = riderRepository.reserveIfAvailable(riderId);
        if (reserved == 0) {
            throw new RuntimeException("Rider is not available");
        }

        // 2) Concurrency-safe order accept.
        Instant now = Instant.now();
        boolean cod = order.getPaymentType() == PaymentType.COD;
        int updated;
        Instant paymentDue = null;
        if (cod) {
            // COD: confirm immediately (no Razorpay / no 60s payment window).
            updated = orderRepository.tryAcceptIncityCodOrder(
                    orderId,
                    riderId,
                    ServiceMode.INCITY,
                    PaymentType.COD,
                    OrderStatus.SEARCHING_RIDER,
                    OrderStatus.CONFIRMED,
                    now,
                    "UNPAID",
                    now);
        } else {
            paymentDue = now.plusSeconds(60);
            updated = orderRepository.tryAcceptOrder(
                    orderId,
                    riderId,
                    ServiceMode.INCITY,
                    OrderStatus.SEARCHING_RIDER,
                    OrderStatus.RIDER_ACCEPTED,
                    now,
                    paymentDue,
                    "UNPAID",
                    now);
        }
        if (updated == 0) {
            // someone else accepted / expired; release rider
            riderRepository.release(riderId);
            throw new RuntimeException("Order already accepted or expired");
        }

        // 3) Close request for other riders.
        dispatchService.closeRequest(orderId, "accepted", riderId);
        final OrderStatus statusAfterAccept = cod ? OrderStatus.CONFIRMED : OrderStatus.RIDER_ACCEPTED;

        // Persist delivery OTP once (COD → CONFIRMED; ONLINE → RIDER_ACCEPTED).
        OrderEntity refreshed = orderRepository.findById(orderId).orElse(order);
        if (refreshed.getDeliveryOtp() == null) {
            refreshed.setDeliveryOtp(DeliveryOtpGenerator.generate());
            refreshed.setDeliveryOtpGeneratedAt(Instant.now());
            refreshed = orderRepository.save(refreshed);
        }
        if (refreshed.getPickupRiderId() == null) {
            refreshed.setPickupRiderId(riderId);
            refreshed.setDeliveryRiderId(riderId);
            refreshed = orderRepository.save(refreshed);
        }
        appendTimeline(refreshed, statusAfterAccept, "rider_accepted", refreshed.getOriginHubId(), riderId, "Rider accepted order");

        // 4) Notify user + rider topic after commit so HTTP (e.g. POST /payments/create-order) sees RIDER_ACCEPTED.
        final Long userId = order.getUserId();
        final Long acceptedOrderId = orderId;
        final Instant paymentDueFinal = paymentDue;
        final boolean codFinal = cod;
        final Long riderIdFinal = riderId;
        final String serviceModeFinal =
                refreshed.getServiceMode() == null ? null : refreshed.getServiceMode().name();
        TransactionAfterCommit.run(() -> {
            if (codFinal) {
                sendUserEvent(userId, acceptedOrderId, "rider_found", OrderStatus.CONFIRMED, null, riderIdFinal);
                sendUserEvent(userId, acceptedOrderId, "confirmed", OrderStatus.CONFIRMED, null, riderIdFinal);
                notificationService.sendToUser(
                        userId,
                        "Rider assigned",
                        "A rider accepted order #" + acceptedOrderId + ". Your delivery is confirmed.",
                        userRiderAcceptedPushData(acceptedOrderId, OrderStatus.CONFIRMED, null, riderIdFinal),
                        NotificationType.USER_RIDER_ACCEPTED);
            } else {
                sendUserEvent(userId, acceptedOrderId, "rider_found", OrderStatus.RIDER_ACCEPTED, paymentDueFinal, riderIdFinal);
                sendUserEvent(userId, acceptedOrderId, "payment_required", OrderStatus.RIDER_ACCEPTED, paymentDueFinal, riderIdFinal);
                notificationService.sendToUser(
                        userId,
                        "Rider accepted",
                        "Complete payment within 60 seconds to confirm order #" + acceptedOrderId + ".",
                        userRiderAcceptedPushData(acceptedOrderId, OrderStatus.RIDER_ACCEPTED, paymentDueFinal, riderIdFinal),
                        NotificationType.USER_RIDER_ACCEPTED);
            }
            riderActiveOrderTopicPublisher.publish(riderIdFinal, acceptedOrderId, statusAfterAccept, "assigned");
            userActiveOrderTopicPublisher.publishStatusUpdated(
                    userId,
                    acceptedOrderId,
                    statusAfterAccept.name(),
                    serviceModeFinal,
                    riderIdFinal);
            adminOrderTopicPublisher.publishStatusUpdated(
                    orderRepository.findById(acceptedOrderId).orElse(null));
        });

        response.setData(orderServiceImpl.toOrderDtoForRider(refreshed));
        response.setMessage("Order accepted");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> markPickedUp(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found"));
        requireAssignedRider(order, riderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order must be CONFIRMED to mark picked up");
        }
        transitionStatus(order, OrderStatus.PICKED_UP);
        OrderEntity saved = orderRepository.save(order);
        appendTimeline(saved, saved.getStatus(), "picked_up", saved.getOriginHubId(), riderId, "Parcel picked by rider");
        sendTypedUserEvent(saved.getUserId(), saved.getId(), "status_updated", saved.getStatus(), riderId);
        adminOrderTopicPublisher.publishStatusUpdated(saved);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(),
                saved.getId(),
                saved.getStatus().name(),
                saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                riderId);
        sendUserOrderStatusPush(saved);
        riderActiveOrderTopicPublisher.publish(
                riderId,
                saved.getId(),
                saved.getStatus(),
                "status_updated",
                resolveCollectAmount(saved));
        response.setData(orderServiceImpl.toOrderDtoForRider(saved));
        response.setMessage("Pickup recorded");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> startTransit(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found"));
        requireAssignedRider(order, riderId);
        if (order.getServiceMode() == ServiceMode.OUTSTATION) {
            if (order.getStatus() == OrderStatus.AT_ORIGIN_HUB) {
                transitionStatus(order, OrderStatus.DEPARTED_ORIGIN_HUB);
            } else if (order.getStatus() == OrderStatus.DEPARTED_ORIGIN_HUB) {
                transitionStatus(order, OrderStatus.IN_TRANSIT);
            } else {
                throw new BadRequestException("OUTSTATION order must be AT_ORIGIN_HUB or DEPARTED_ORIGIN_HUB to start transit");
            }
        } else {
            if (order.getStatus() != OrderStatus.PICKED_UP) {
                throw new BadRequestException("Order must be PICKED_UP to start transit");
            }
            transitionStatus(order, OrderStatus.IN_TRANSIT);
        }
        OrderEntity saved = orderRepository.save(order);
        appendTimeline(saved, saved.getStatus(), "in_transit", saved.getOriginHubId(), riderId, "Rider started transit");
        sendTypedUserEvent(saved.getUserId(), saved.getId(), "status_updated", saved.getStatus(), riderId);
        adminOrderTopicPublisher.publishStatusUpdated(saved);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(),
                saved.getId(),
                saved.getStatus().name(),
                saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                riderId);
        sendUserOrderStatusPush(saved);
        riderActiveOrderTopicPublisher.publish(
                riderId,
                saved.getId(),
                saved.getStatus(),
                "status_updated",
                resolveCollectAmount(saved));
        response.setData(orderServiceImpl.toOrderDtoForRider(saved));
        response.setMessage("Transit started");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> reachDestination(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found"));
        requireAssignedRider(order, riderId);
        if (order.getServiceMode() == ServiceMode.OUTSTATION) {
            if (order.getStatus() != OrderStatus.PICKED_UP) {
                throw new BadRequestException("OUTSTATION order must be PICKED_UP to record origin hub arrival");
            }
            transitionStatus(order, OrderStatus.AT_ORIGIN_HUB);
        } else if (order.getStatus() != OrderStatus.IN_TRANSIT) {
            throw new BadRequestException("Order must be IN_TRANSIT to record destination arrival");
        }
        OrderEntity saved = orderRepository.save(order);
        sendTypedUserEvent(saved.getUserId(), saved.getId(), "reach_destination", saved.getStatus(), riderId);
        adminOrderTopicPublisher.publish("reach_destination", saved);
        appendTimeline(saved, saved.getStatus(), "reach_destination", saved.getDestinationHubId(), riderId, "Rider reached destination");
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(),
                saved.getId(),
                saved.getStatus().name(),
                saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                riderId);
        riderActiveOrderTopicPublisher.publish(
                riderId,
                saved.getId(),
                saved.getStatus(),
                "reach_destination",
                resolveCollectAmount(saved));
        response.setData(orderServiceImpl.toOrderDtoForRider(saved));
        response.setMessage("Destination reached");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    @Override
    public ApiResponse<String> reject(Long riderId, Long orderId) {
        ApiResponse<String> response = new ApiResponse<>();
        if (riderId == null || orderId == null) {
            response.setMessage("riderId and orderId are required");
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
            return response;
        }
        if (!dispatchService.wasRiderDispatched(orderId, riderId)) {
            throw new RuntimeException("Rider was not dispatched for this order");
        }
        dispatchService.markRejected(orderId, riderId);
        response.setData("Rejected");
        response.setMessage("Rejected");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    private static Map<String, String> userRiderAcceptedPushData(
            Long orderId, OrderStatus status, Instant paymentDueAt, Long riderId) {
        Map<String, String> d = new HashMap<>(NotificationService.baseData(
                orderId,
                status != null ? status.name() : null,
                NotificationType.USER_RIDER_ACCEPTED));
        if (paymentDueAt != null) {
            d.put("paymentDueAtEpochMs", String.valueOf(paymentDueAt.toEpochMilli()));
        }
        if (riderId != null) {
            d.put("riderId", String.valueOf(riderId));
        }
        return d;
    }

    private void sendUserEvent(Long userId, Long orderId, String event, OrderStatus status, Instant paymentDueAt,
            Long riderId) {
        if (userId == null) {
            return;
        }
        UserOrderEventDTO dto = new UserOrderEventDTO();
        dto.setOrderId(orderId);
        dto.setEvent(event);
        dto.setEventType(event);
        dto.setEventVersion(1);
        dto.setTsEpochMs(Instant.now().toEpochMilli());
        dto.setSource("backend");
        dto.setStatus(status == null ? null : status.name());
        dto.setStage(status == null ? null : status.name());
        dto.setPaymentDueAtEpochMs(paymentDueAt == null ? null : paymentDueAt.toEpochMilli());
        dto.setRiderId(riderId);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", dto);
    }

    private void sendTypedUserEvent(Long userId, Long orderId, String eventType, OrderStatus status, Long riderId) {
        if (userId == null) {
            return;
        }
        UserOrderEventDTO dto = new UserOrderEventDTO();
        dto.setOrderId(orderId);
        dto.setEvent(eventType);
        dto.setEventType(eventType);
        dto.setEventVersion(1);
        dto.setTsEpochMs(Instant.now().toEpochMilli());
        dto.setSource("backend");
        dto.setStatus(status == null ? null : status.name());
        dto.setStage(status == null ? null : status.name());
        dto.setRiderId(riderId);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", dto);
    }

    private void sendUserOrderStatusPush(OrderEntity order) {
        if (order == null || order.getUserId() == null || order.getId() == null || order.getStatus() == null) {
            return;
        }
        String readableStatus = order.getStatus().name().replace('_', ' ');
        notificationService.sendToUser(
                order.getUserId(),
                "Order update",
                "Order #" + order.getId() + " is now " + readableStatus,
                NotificationService.baseData(
                        order.getId(),
                        order.getStatus().name(),
                        NotificationType.USER_ORDER_STATUS_UPDATE),
                NotificationType.USER_ORDER_STATUS_UPDATE);
    }

    private static void requireAssignedRider(OrderEntity order, Long riderId) {
        final Long assigned = order.getDeliveryRiderId() != null ? order.getDeliveryRiderId() : order.getRiderId();
        if (assigned == null || !assigned.equals(riderId)) {
            throw new BadRequestException("Access denied");
        }
    }

    private void transitionStatus(OrderEntity order, OrderStatus toStatus) {
        orderStatusTransitionGuard.ensureAllowed(order.getServiceMode(), order.getStatus(), toStatus);
        order.setStatus(toStatus);
    }

    private void appendTimeline(
            OrderEntity order,
            OrderStatus status,
            String eventType,
            Long hubId,
            Long riderId,
            String notes) {
        orderTimelineService.appendEvent(order, status, eventType, hubId, riderId, null, notes);
    }

    private static Double resolveCollectAmount(OrderEntity order) {
        if (order == null || order.getPaymentType() != PaymentType.COD) {
            return null;
        }
        return order.getTotalAmount();
    }

    @SuppressWarnings("unused")
    private RiderEntity requireRider(Long riderId) {
        return riderRepository.findById(riderId).orElseThrow(() -> new RuntimeException("Rider not found"));
    }

}

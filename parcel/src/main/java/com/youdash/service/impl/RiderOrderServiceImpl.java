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
import com.youdash.util.DeliveryOtpGenerator;
import com.youdash.util.TransactionAfterCommit;
import com.youdash.service.DispatchService;
import com.youdash.service.NotificationService;
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

        // Persist delivery OTP once (COD → CONFIRMED; ONLINE → RIDER_ACCEPTED).
        OrderEntity refreshed = orderRepository.findById(orderId).orElse(order);
        if (refreshed.getDeliveryOtp() == null) {
            refreshed.setDeliveryOtp(DeliveryOtpGenerator.generate());
            refreshed = orderRepository.save(refreshed);
        }

        // 4) Notify user + rider topic after commit so HTTP (e.g. POST /payments/create-order) sees RIDER_ACCEPTED.
        final Long userId = order.getUserId();
        final Long acceptedOrderId = orderId;
        final Instant paymentDueFinal = paymentDue;
        final boolean codFinal = cod;
        final Long riderIdFinal = riderId;
        final OrderStatus statusAfterAccept = cod ? OrderStatus.CONFIRMED : OrderStatus.RIDER_ACCEPTED;
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
        requireAssignedIncityRider(order, riderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new BadRequestException("Order must be CONFIRMED to mark picked up");
        }
        order.setStatus(OrderStatus.PICKED_UP);
        OrderEntity saved = orderRepository.save(order);
        sendTypedUserEvent(saved.getUserId(), saved.getId(), "status_updated", saved.getStatus(), riderId);
        riderActiveOrderTopicPublisher.publish(riderId, saved.getId(), saved.getStatus(), "status_updated");
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
        requireAssignedIncityRider(order, riderId);
        if (order.getStatus() != OrderStatus.PICKED_UP) {
            throw new BadRequestException("Order must be PICKED_UP to start transit");
        }
        order.setStatus(OrderStatus.IN_TRANSIT);
        OrderEntity saved = orderRepository.save(order);
        sendTypedUserEvent(saved.getUserId(), saved.getId(), "status_updated", saved.getStatus(), riderId);
        riderActiveOrderTopicPublisher.publish(riderId, saved.getId(), saved.getStatus(), "status_updated");
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
        requireAssignedIncityRider(order, riderId);
        if (order.getStatus() != OrderStatus.IN_TRANSIT) {
            throw new BadRequestException("Order must be IN_TRANSIT to record destination arrival");
        }
        sendTypedUserEvent(order.getUserId(), order.getId(), "reach_destination", order.getStatus(), riderId);
        riderActiveOrderTopicPublisher.publish(riderId, order.getId(), order.getStatus(), "reach_destination");
        response.setData(orderServiceImpl.toOrderDtoForRider(order));
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
        dto.setStatus(status == null ? null : status.name());
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
        dto.setStatus(status == null ? null : status.name());
        dto.setRiderId(riderId);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", dto);
    }

    private static void requireAssignedIncityRider(OrderEntity order, Long riderId) {
        if (order.getServiceMode() != ServiceMode.INCITY) {
            throw new BadRequestException("Only INCITY orders support this action");
        }
        if (order.getRiderId() == null || !order.getRiderId().equals(riderId)) {
            throw new BadRequestException("Access denied");
        }
    }

    @SuppressWarnings("unused")
    private RiderEntity requireRider(Long riderId) {
        return riderRepository.findById(riderId).orElseThrow(() -> new RuntimeException("Rider not found"));
    }

}

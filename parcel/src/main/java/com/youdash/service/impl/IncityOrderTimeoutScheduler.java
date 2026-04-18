package com.youdash.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.dto.realtime.UserOrderEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.notification.NotificationType;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.service.DispatchService;
import com.youdash.service.NotificationService;

@Service
public class IncityOrderTimeoutScheduler {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Scheduled(fixedDelayString = "${incity.scheduler.delay-ms:3000}")
    @Transactional(rollbackFor = Exception.class)
    public void expireSearchingOrders() {
        Instant now = Instant.now();
        List<OrderEntity> expired = orderRepository.findByServiceModeAndStatusAndSearchExpiresAtBefore(
                ServiceMode.INCITY, OrderStatus.SEARCHING_RIDER, now);
        for (OrderEntity o : expired) {
            int updated = orderRepository.updateStatusWithReason(
                    o.getId(),
                    ServiceMode.INCITY,
                    OrderStatus.SEARCHING_RIDER,
                    OrderStatus.EXPIRED,
                    "NO_RIDER_FOUND",
                    o.getPaymentStatus());
            if (updated == 1) {
                dispatchService.closeRequest(o.getId(), "expired", null);
                sendUserCancelled(o.getUserId(), o.getId(), "NO_RIDER_FOUND", OrderStatus.EXPIRED);
                pushUserOrderClosed(
                        o.getUserId(),
                        o.getId(),
                        OrderStatus.EXPIRED,
                        "NO_RIDER_FOUND",
                        "No rider available",
                        "We could not find a rider in time for order #" + o.getId() + ".");
            }
        }
    }

    @Scheduled(fixedDelayString = "${incity.scheduler.delay-ms:3000}")
    @Transactional(rollbackFor = Exception.class)
    public void cancelPaymentTimeoutOrders() {
        Instant now = Instant.now();
        // Only ONLINE orders should be subject to the post-accept payment window.
        List<OrderEntity> timedOut = orderRepository.findByServiceModeAndPaymentTypeAndStatusInAndPaymentDueAtBefore(
                ServiceMode.INCITY,
                PaymentType.ONLINE,
                List.of(OrderStatus.RIDER_ACCEPTED, OrderStatus.PAYMENT_PENDING),
                now);

        for (OrderEntity o : timedOut) {
            if ("PAID".equalsIgnoreCase(o.getPaymentStatus())) {
                continue;
            }
            OrderStatus expected = o.getStatus();
            if (!(expected == OrderStatus.RIDER_ACCEPTED || expected == OrderStatus.PAYMENT_PENDING)) {
                continue;
            }
            int updated = orderRepository.updateStatusWithReason(
                    o.getId(),
                    ServiceMode.INCITY,
                    expected,
                    OrderStatus.CANCELLED,
                    "PAYMENT_TIMEOUT",
                    "FAILED");
            if (updated == 1) {
                if (o.getRiderId() != null) {
                    riderRepository.release(o.getRiderId());
                }
                dispatchService.closeRequest(o.getId(), "cancelled", null);
                sendUserCancelled(o.getUserId(), o.getId(), "PAYMENT_TIMEOUT", OrderStatus.CANCELLED);
                pushUserOrderClosed(
                        o.getUserId(),
                        o.getId(),
                        OrderStatus.CANCELLED,
                        "PAYMENT_TIMEOUT",
                        "Payment timeout",
                        "Payment was not completed in time for order #" + o.getId() + ".");
            }
        }
    }

    /**
     * Safety cleanup: release riders stuck as unavailable but with no active INCITY order.
     * Covers restart scenarios or unexpected failures.
     */
    @Scheduled(fixedDelayString = "${incity.scheduler.rider-cleanup-delay-ms:60000}")
    @Transactional(rollbackFor = Exception.class)
    public void releaseOrphanLockedRiders() {
        List<OrderStatus> active = List.of(
                OrderStatus.RIDER_ACCEPTED,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.CONFIRMED,
                OrderStatus.PICKED_UP,
                OrderStatus.IN_TRANSIT);

        for (var rider : riderRepository.findByIsAvailableFalse()) {
            if (rider == null || rider.getId() == null) {
                continue;
            }
            boolean hasActive = orderRepository.existsByRiderIdAndServiceModeAndStatusIn(
                    rider.getId(), ServiceMode.INCITY, active);
            if (!hasActive) {
                riderRepository.release(rider.getId());
            }
        }
    }

    private void sendUserCancelled(Long userId, Long orderId, String reason, OrderStatus status) {
        if (userId == null) {
            return;
        }
        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(orderId);
        evt.setEvent("cancelled");
        evt.setStatus(status == null ? null : status.name());
        evt.setPaymentDueAtEpochMs(null);
        evt.setRiderId(null);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", evt);
    }

    private void pushUserOrderClosed(
            Long userId,
            Long orderId,
            OrderStatus status,
            String cancelReason,
            String title,
            String body) {
        if (userId == null || orderId == null) {
            return;
        }
        Map<String, String> data = new HashMap<>(
                NotificationService.baseData(
                        orderId,
                        status != null ? status.name() : null,
                        NotificationType.USER_ORDER_CLOSED));
        if (cancelReason != null) {
            data.put("cancelReason", cancelReason);
        }
        notificationService.sendToUser(userId, title, body, data, NotificationType.USER_ORDER_CLOSED);
    }
}


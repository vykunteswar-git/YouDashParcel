package com.youdash.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.dto.realtime.UserOrderEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.notification.NotificationType;
import com.youdash.realtime.AdminOrderTopicPublisher;
import com.youdash.realtime.UserActiveOrderTopicPublisher;
import com.youdash.repository.OrderRepository;
import com.youdash.service.NotificationService;
import com.youdash.util.OutstationOnlinePrepayPolicy;

@Service
public class OutstationPaymentTimeoutScheduler {

    @Value("${outstation.prepay.window-seconds:1800}")
    private long prepayWindowSeconds;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Autowired
    private AdminOrderTopicPublisher adminOrderTopicPublisher;

    @Scheduled(fixedDelayString = "${outstation.scheduler.delay-ms:3000}")
    @Transactional(rollbackFor = Exception.class)
    public void expireUnpaidBookedOrders() {
        Instant now = Instant.now();
        Set<Long> seen = new HashSet<>();

        List<OrderEntity> timedOut = orderRepository.findByServiceModeAndPaymentTypeAndStatusInAndPaymentDueAtBefore(
                ServiceMode.OUTSTATION,
                PaymentType.ONLINE,
                List.of(OrderStatus.BOOKED),
                now);
        for (OrderEntity order : timedOut) {
            if (order.getId() != null) {
                seen.add(order.getId());
            }
            expireIfStillAwaitingPrepay(order, now);
        }

        List<OrderEntity> legacyWithoutDue = orderRepository
                .findByServiceModeAndPaymentTypeAndStatusAndPaymentDueAtIsNull(
                        ServiceMode.OUTSTATION,
                        PaymentType.ONLINE,
                        OrderStatus.BOOKED);
        for (OrderEntity order : legacyWithoutDue) {
            if (order.getId() == null || seen.contains(order.getId())) {
                continue;
            }
            if (!OutstationOnlinePrepayPolicy.isPaymentWindowExpired(order, now, prepayWindowSeconds)) {
                continue;
            }
            expireIfStillAwaitingPrepay(order, now);
        }
    }

    private void expireIfStillAwaitingPrepay(OrderEntity order, Instant now) {
        if (!OutstationOnlinePrepayPolicy.isAwaitingPrepay(order)) {
            return;
        }
        if (order.getPaymentStatus() != null && "FAILED".equalsIgnoreCase(order.getPaymentStatus().trim())) {
            cancelOnPaymentFailure(order);
            return;
        }
        if (order.getPaymentDueAt() != null && !order.getPaymentDueAt().isBefore(now)) {
            return;
        }
        if (order.getPaymentDueAt() == null
                && !OutstationOnlinePrepayPolicy.isPaymentWindowExpired(order, now, prepayWindowSeconds)) {
            return;
        }

        int updated = orderRepository.updateStatusWithReason(
                order.getId(),
                ServiceMode.OUTSTATION,
                OrderStatus.BOOKED,
                OrderStatus.EXPIRED,
                "PAYMENT_TIMEOUT",
                "FAILED");
        if (updated != 1) {
            return;
        }

        order.setStatus(OrderStatus.EXPIRED);
        order.setCancelReason("PAYMENT_TIMEOUT");
        order.setPaymentStatus("FAILED");
        sendUserClosed(order.getUserId(), order.getId(), OrderStatus.EXPIRED, "PAYMENT_TIMEOUT");
        userActiveOrderTopicPublisher.publishReleased(order.getUserId(), order.getId());
        adminOrderTopicPublisher.publishStatusUpdated(order);
        pushUserOrderClosed(
                order.getUserId(),
                order.getId(),
                OrderStatus.EXPIRED,
                "PAYMENT_TIMEOUT",
                "Payment window expired",
                "Payment time expired for order #" + order.getId() + ". Please create a new order.");
    }

    private void cancelOnPaymentFailure(OrderEntity order) {
        int updated = orderRepository.updateStatusWithReason(
                order.getId(),
                ServiceMode.OUTSTATION,
                OrderStatus.BOOKED,
                OrderStatus.CANCELLED,
                "PAYMENT_FAILED",
                "FAILED");
        if (updated != 1) {
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelReason("PAYMENT_FAILED");
        order.setPaymentStatus("FAILED");
        sendUserClosed(order.getUserId(), order.getId(), OrderStatus.CANCELLED, "PAYMENT_FAILED");
        userActiveOrderTopicPublisher.publishReleased(order.getUserId(), order.getId());
        adminOrderTopicPublisher.publishStatusUpdated(order);
        pushUserOrderClosed(
                order.getUserId(),
                order.getId(),
                OrderStatus.CANCELLED,
                "PAYMENT_FAILED",
                "Payment failed",
                "Payment failed for order #" + order.getId() + ". Please create a new order.");
    }

    private void sendUserClosed(Long userId, Long orderId, OrderStatus status, String reason) {
        if (userId == null) {
            return;
        }
        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(orderId);
        evt.setEvent("cancelled");
        evt.setEventType("cancelled");
        evt.setEventVersion(1);
        evt.setTsEpochMs(Instant.now().toEpochMilli());
        evt.setSource("backend");
        evt.setStatus(status == null ? null : status.name());
        evt.setServiceMode(ServiceMode.OUTSTATION.name());
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

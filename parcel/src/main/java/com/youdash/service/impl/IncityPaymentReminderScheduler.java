package com.youdash.service.impl;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderRepository;
import com.youdash.service.NotificationDedupService;
import com.youdash.service.NotificationService;

/**
 * Reminds the customer to complete Razorpay payment before the post-accept window closes.
 */
@Service
public class IncityPaymentReminderScheduler {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDedupService notificationDedupService;

    /** Seconds of payment time remaining: lower bound (exclusive) for reminder window. */
    @Value("${incity.payment-reminder.remaining-seconds-min:5}")
    private long remainingSecondsMin;

    /** Seconds of payment time remaining: upper bound (inclusive) for reminder window. */
    @Value("${incity.payment-reminder.remaining-seconds-max:35}")
    private long remainingSecondsMax;

    @Scheduled(fixedDelayString = "${incity.payment-reminder.scheduler-delay-ms:5000}")
    @Transactional(readOnly = true)
    public void remindPendingPayments() {
        if (remainingSecondsMax <= remainingSecondsMin) {
            return;
        }
        Instant now = Instant.now();
        Instant nowPlusMin = now.plusSeconds(remainingSecondsMin);
        Instant nowPlusMax = now.plusSeconds(remainingSecondsMax);
        List<OrderEntity> due = orderRepository.findIncityOnlinePaymentDueBetween(
                ServiceMode.INCITY,
                PaymentType.ONLINE,
                List.of(OrderStatus.RIDER_ACCEPTED, OrderStatus.PAYMENT_PENDING),
                nowPlusMin,
                nowPlusMax);
        for (OrderEntity o : due) {
            if (o.getUserId() == null || o.getId() == null) {
                continue;
            }
            if (!notificationDedupService.tryAcquire("payment-pending-reminder:" + o.getId())) {
                continue;
            }
            long remainingSec = o.getPaymentDueAt() == null ? 0
                    : Math.max(0, o.getPaymentDueAt().getEpochSecond() - now.getEpochSecond());
            Map<String, String> data = new HashMap<>(
                    NotificationService.baseData(
                            o.getId(),
                            o.getStatus() != null ? o.getStatus().name() : null,
                            NotificationType.PAYMENT_PENDING_REMINDER));
            data.put("secondsRemaining", String.valueOf(remainingSec));
            if (o.getPaymentDueAt() != null) {
                data.put("paymentDueAtEpochMs", String.valueOf(o.getPaymentDueAt().toEpochMilli()));
            }
            String ref = o.getDisplayOrderId() != null ? o.getDisplayOrderId() : "#" + o.getId();
            notificationService.sendToUser(
                    o.getUserId(),
                    "Complete payment",
                    "Pay for order " + ref + " soon — about " + remainingSec + "s left.",
                    data,
                    NotificationType.PAYMENT_PENDING_REMINDER);
        }
    }
}

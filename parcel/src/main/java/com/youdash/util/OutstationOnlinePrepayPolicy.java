package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;

import java.time.Instant;

/**
 * Outstation ONLINE orders must be prepaid while still {@link OrderStatus#BOOKED}.
 */
public final class OutstationOnlinePrepayPolicy {

    private OutstationOnlinePrepayPolicy() {}

    public static boolean isAwaitingPrepay(OrderEntity order) {
        return order != null
                && order.getServiceMode() == ServiceMode.OUTSTATION
                && order.getPaymentType() == PaymentType.ONLINE
                && order.getStatus() == OrderStatus.BOOKED
                && !isPaid(order);
    }

    public static boolean isPaid(OrderEntity order) {
        if (order == null || order.getPaymentStatus() == null) {
            return false;
        }
        return "PAID".equalsIgnoreCase(order.getPaymentStatus().trim());
    }

    public static Instant resolvePaymentDeadline(OrderEntity order, long windowSeconds) {
        if (order == null) {
            return null;
        }
        if (order.getPaymentDueAt() != null) {
            return order.getPaymentDueAt();
        }
        if (order.getCreatedAt() != null && windowSeconds > 0) {
            return order.getCreatedAt().plusSeconds(windowSeconds);
        }
        return null;
    }

    public static boolean isPaymentWindowExpired(OrderEntity order, Instant now, long windowSeconds) {
        if (order == null || now == null) {
            return false;
        }
        Instant deadline = resolvePaymentDeadline(order, windowSeconds);
        return deadline != null && !deadline.isAfter(now);
    }
}

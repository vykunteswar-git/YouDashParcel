package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;

/**
 * Orders that count toward admin revenue / completion metrics.
 * Keep in sync with {@code OrderRepository#findEarningsOrdersInRange}.
 */
public final class AdminRevenueOrderPolicy {

    private AdminRevenueOrderPolicy() {
    }

    public static boolean isCompletedForRevenue(OrderEntity order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        OrderStatus status = order.getStatus();
        if (status == OrderStatus.DELIVERED) {
            return true;
        }
        String deliveryType = normalizeDeliveryType(order.getDeliveryType());
        if (status == OrderStatus.COLLECTED && "DOOR_TO_HUB".equals(deliveryType)) {
            return true;
        }
        return status == OrderStatus.BOOKED && "HUB_TO_HUB".equals(deliveryType);
    }

    private static String normalizeDeliveryType(String deliveryType) {
        if (deliveryType == null || deliveryType.isBlank()) {
            return "";
        }
        return deliveryType.trim().toUpperCase().replace('-', '_');
    }
}

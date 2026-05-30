package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.ServiceMode;

import java.util.Objects;

/**
 * Outstation COD is always collected from the sender (booking user), never from the receiver.
 */
public final class OutstationCodPolicy {

    private OutstationCodPolicy() {}

    public static boolean isOutstation(OrderEntity order) {
        return order != null && order.getServiceMode() == ServiceMode.OUTSTATION;
    }

    public static String deliveryTypeUpper(OrderEntity order) {
        if (order == null || order.getDeliveryType() == null) {
            return "";
        }
        return order.getDeliveryType().trim().toUpperCase();
    }

    public static boolean isHubToDoor(OrderEntity order) {
        return "HUB_TO_DOOR".equals(deliveryTypeUpper(order));
    }

    public static boolean isDoorToHub(OrderEntity order) {
        return "DOOR_TO_HUB".equals(deliveryTypeUpper(order));
    }

    /** Rider collects COD at sender door (pickup leg). */
    public static boolean pickupRiderCollectsCod(OrderEntity order) {
        if (!isOutstation(order)) {
            return true;
        }
        return !isHubToDoor(order);
    }

    /**
     * Rider id allowed to call {@code /payments/collect} for this order.
     * HUB_TO_DOOR: hub/admin records COD at drop (no pickup rider).
     */
    public static Long resolveCodCollectorRiderId(OrderEntity order) {
        if (order == null) {
            return null;
        }
        if (!isOutstation(order)) {
            return order.getRiderId();
        }
        if (!pickupRiderCollectsCod(order)) {
            return null;
        }
        if (order.getPickupRiderId() != null) {
            return order.getPickupRiderId();
        }
        return order.getRiderId();
    }
}

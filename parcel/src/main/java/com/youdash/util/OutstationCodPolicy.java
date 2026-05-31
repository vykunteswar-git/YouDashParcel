package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.ServiceMode;


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
        return isHubToDoor(deliveryTypeUpper(order));
    }

    public static boolean isHubToDoor(String deliveryType) {
        return "HUB_TO_DOOR".equals(deliveryType == null ? "" : deliveryType.trim().toUpperCase());
    }

    public static boolean isDoorToHub(OrderEntity order) {
        return isDoorToHub(deliveryTypeUpper(order));
    }

    public static boolean isDoorToHub(String deliveryType) {
        return "DOOR_TO_HUB".equals(deliveryType == null ? "" : deliveryType.trim().toUpperCase());
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

    /** OUTSTATION D2D/D2H: COD is taken from the sender at pickup, not at delivery. */
    public static boolean codCollectedAtPickupLeg(OrderEntity order) {
        return isOutstation(order) && pickupRiderCollectsCod(order);
    }

    /**
     * Socket / UI collect hint for a specific rider. Null when COD already recorded or this rider
     * is not the collector (e.g. delivery rider on a split D2D leg).
     */
    public static Double resolveRiderCollectAmount(OrderEntity order, Long riderId) {
        if (order == null || order.getPaymentType() != com.youdash.model.PaymentType.COD) {
            return null;
        }
        if (order.getCodCollectedAmount() != null && order.getCodCollectedAmount() > 0.0) {
            return null;
        }
        Long collector = resolveCodCollectorRiderId(order);
        if (collector != null && riderId != null && !java.util.Objects.equals(collector, riderId)) {
            return null;
        }
        return order.getTotalAmount();
    }
}

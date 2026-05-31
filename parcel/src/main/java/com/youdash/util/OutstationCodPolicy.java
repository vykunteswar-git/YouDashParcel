package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;

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

    public static boolean isDoorToDoor(OrderEntity order) {
        return isDoorToDoor(deliveryTypeUpper(order));
    }

    public static boolean isDoorToDoor(String deliveryType) {
        return "DOOR_TO_DOOR".equals(deliveryType == null ? "" : deliveryType.trim().toUpperCase());
    }

    /**
     * Delivery rider for outstation last-mile settlement.
     * Prefer {@code deliveryRiderId}; when only {@code riderId} differs from pickup, treat as delivery.
     */
    public static Long resolveDeliveryRiderId(OrderEntity order) {
        if (order == null) {
            return null;
        }
        if (order.getDeliveryRiderId() != null) {
            return order.getDeliveryRiderId();
        }
        if (!isOutstation(order)) {
            return order.getRiderId();
        }
        Long pickup = OutstationRiderLegPolicy.resolvePickupRiderId(order);
        Long rider = order.getRiderId();
        if (pickup != null && rider != null && !Objects.equals(pickup, rider)) {
            return rider;
        }
        if (isDoorToDoor(order) && rider != null && pickup == null) {
            return rider;
        }
        return null;
    }

    /** D2D with distinct pickup and delivery riders — each leg settles separately. */
    public static boolean hasSplitPickupAndDeliveryRiders(OrderEntity order) {
        if (!isOutstation(order) || !isDoorToDoor(order)) {
            return false;
        }
        Long pickup = OutstationRiderLegPolicy.resolvePickupRiderId(order);
        Long delivery = resolveDeliveryRiderId(order);
        return pickup != null && delivery != null && !Objects.equals(pickup, delivery);
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
     * Rider should receive a normal withdrawable wallet credit (like ONLINE) because COD cash was
     * already collected by another party (pickup rider or hub/admin) and remitted — this rider only
     * performed the delivery leg.
     */
    public static boolean riderEarnsWalletCreditWithoutCodCash(OrderEntity order, Long riderId) {
        if (order == null || riderId == null || order.getPaymentType() != PaymentType.COD) {
            return false;
        }
        if (codAmount(order) <= 0.0) {
            return false;
        }
        Long collector = resolveCodCollectorRiderId(order);
        if (collector == null) {
            return isOutstation(order);
        }
        return !Objects.equals(collector, riderId);
    }

    /** H2D, or D2D/H2D delivery-only completion on last mile (not split D2D). */
    public static boolean isDeliveryLegOnlyOrder(OrderEntity order) {
        if (!isOutstation(order)) {
            return false;
        }
        if (isHubToDoor(order)) {
            return true;
        }
        if (hasSplitPickupAndDeliveryRiders(order)) {
            return false;
        }
        if (isDoorToDoor(order) && order.getDeliveryRiderId() != null) {
            return true;
        }
        Long pickup = order.getPickupRiderId();
        Long delivery = order.getDeliveryRiderId();
        return delivery != null && pickup == null;
    }

    /** True when this rider collected COD cash and must not receive withdrawable wallet credit. */
    public static boolean riderHoldsCodCash(OrderEntity order, Long riderId) {
        if (order == null || riderId == null || order.getPaymentType() != PaymentType.COD) {
            return false;
        }
        if (order.getCodCollectionMode() == CodCollectionMode.QR) {
            return false;
        }
        Long collector = resolveCodCollectorRiderId(order);
        if (collector != null) {
            return Objects.equals(collector, riderId);
        }
        return !isOutstation(order) && Objects.equals(order.getRiderId(), riderId);
    }

    private static double codAmount(OrderEntity order) {
        return order.getCodCollectedAmount() != null ? order.getCodCollectedAmount() : 0.0;
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

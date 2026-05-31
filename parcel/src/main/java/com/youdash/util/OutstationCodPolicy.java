package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;

import java.util.Locale;
import java.util.Objects;
import java.util.function.BiPredicate;


/**
 * Outstation COD is always collected from the sender (booking user), never from the receiver.
 */
public final class OutstationCodPolicy {

    private OutstationCodPolicy() {}

    public static boolean isOutstation(OrderEntity order) {
        return order != null && order.getServiceMode() == ServiceMode.OUTSTATION;
    }

    public static String normalizeDeliveryType(String deliveryType) {
        if (deliveryType == null || deliveryType.isBlank()) {
            return "";
        }
        return deliveryType.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    public static String deliveryTypeUpper(OrderEntity order) {
        if (order == null || order.getDeliveryType() == null) {
            return "";
        }
        return normalizeDeliveryType(order.getDeliveryType());
    }

    public static boolean isHubToDoor(OrderEntity order) {
        return isHubToDoor(deliveryTypeUpper(order));
    }

    public static boolean isHubToDoor(String deliveryType) {
        return "HUB_TO_DOOR".equals(normalizeDeliveryType(deliveryType));
    }

    public static boolean isDoorToHub(OrderEntity order) {
        return isDoorToHub(deliveryTypeUpper(order));
    }

    public static boolean isDoorToHub(String deliveryType) {
        return "DOOR_TO_HUB".equals(normalizeDeliveryType(deliveryType));
    }

    public static boolean isDoorToDoor(OrderEntity order) {
        return isDoorToDoor(deliveryTypeUpper(order));
    }

    public static boolean isDoorToDoor(String deliveryType) {
        return "DOOR_TO_DOOR".equals(normalizeDeliveryType(deliveryType));
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
        if (rider != null && pickup == null && (isDoorToDoor(order) || isHubToDoor(order))) {
            return rider;
        }
        return null;
    }

    /** True when {@code riderId} is the outstation last-mile (drop) rider for this order. */
    public static boolean isOutstationLastMileRider(OrderEntity order, Long riderId) {
        if (order == null || riderId == null || !isOutstation(order)) {
            return false;
        }
        Long deliveryId = resolveDeliveryRiderId(order);
        return deliveryId != null && Objects.equals(deliveryId, riderId);
    }

    /**
     * DELIVERED outstation order still missing withdrawable wallet credit for the delivery rider.
     */
    public static boolean needsDeliveryWalletCredit(
            OrderEntity order,
            Long deliveryRiderId,
            BiPredicate<Long, Long> hasWalletCredit) {
        if (order == null || deliveryRiderId == null || order.getStatus() != OrderStatus.DELIVERED) {
            return false;
        }
        if (!isOutstation(order) || !isOutstationLastMileRider(order, deliveryRiderId)) {
            return false;
        }
        if (hasWalletCredit.test(deliveryRiderId, order.getId())) {
            return false;
        }
        if (isHubToDoor(order)) {
            return true;
        }
        if (isDoorToDoor(order)) {
            Long pickup = OutstationRiderLegPolicy.resolvePickupRiderId(order);
            return pickup == null || !Objects.equals(pickup, deliveryRiderId);
        }
        return false;
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
        // D2D/D2H: after delivery assign, riderId often equals deliveryRiderId — not the pickup collector.
        Long deliveryId = resolveDeliveryRiderId(order);
        if (deliveryId != null && Objects.equals(order.getRiderId(), deliveryId)) {
            return null;
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
        // D2D/D2H: COD is from sender at pickup — last-mile rider always gets withdrawable wallet credit.
        if (codCollectedAtPickupLeg(order) && isOutstationLastMileRider(order, riderId)) {
            return true;
        }
        Long collector = resolveCodCollectorRiderId(order);
        if (collector == null) {
            return isOutstation(order) && isOutstationLastMileRider(order, riderId);
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
        if (codCollectedAtPickupLeg(order) && isOutstationLastMileRider(order, riderId)) {
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

package com.youdash.util;

import java.util.Objects;
import java.util.Set;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;

/**
 * Outstation split-leg rules: pickup rider is done after origin hub handover;
 * delivery rider owns the later hub / customer leg.
 */
public final class OutstationRiderLegPolicy {

    private static final Set<OrderStatus> PICKUP_RIDER_DONE_STATUSES = Set.of(
            OrderStatus.AT_ORIGIN_HUB,
            OrderStatus.IN_TRANSIT,
            OrderStatus.AT_DESTINATION_HUB,
            OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.AWAITING_HUB_COLLECTION,
            OrderStatus.DELIVERED,
            OrderStatus.COLLECTED);

    private OutstationRiderLegPolicy() {}

    public static Long resolvePickupRiderId(OrderEntity order) {
        if (order == null) {
            return null;
        }
        return order.getPickupRiderId() != null ? order.getPickupRiderId() : order.getRiderId();
    }

    /**
     * Split-leg pickup rider finished once the order reached origin hub or progressed further.
     * Same rider on both legs is not treated as complete until full delivery.
     */
    public static boolean isSplitPickupRiderLegComplete(OrderEntity order, Long riderId) {
        if (order == null || riderId == null) {
            return false;
        }
        if (order.getServiceMode() != ServiceMode.OUTSTATION) {
            return false;
        }
        Long pickupId = resolvePickupRiderId(order);
        if (!Objects.equals(riderId, pickupId)) {
            return false;
        }
        Long deliveryId = order.getDeliveryRiderId();
        if (deliveryId == null || Objects.equals(pickupId, deliveryId)) {
            return false;
        }
        OrderStatus status = order.getStatus();
        return status != null && PICKUP_RIDER_DONE_STATUSES.contains(status);
    }

    /** Whether this rider should receive {@code hasActiveOrder=true} for the order. */
    public static boolean shouldRiderHaveActiveOrder(OrderEntity order, Long riderId) {
        if (order == null || riderId == null) {
            return false;
        }
        if (order.getServiceMode() != ServiceMode.OUTSTATION) {
            return true;
        }
        return !isSplitPickupRiderLegComplete(order, riderId);
    }

    /**
     * Split pickup rider must not receive hub-transit / delivery socket noise after handover.
     * The only allowed message is the one-time {@code delivered} release at origin hub.
     */
    public static boolean shouldSuppressRiderActiveOrderSocket(
            OrderEntity order, Long riderId, String event, String reason) {
        if (!isSplitPickupRiderLegComplete(order, riderId)) {
            return false;
        }
        return !("delivered".equals(event) && "pickup_leg_complete".equals(reason));
    }
}

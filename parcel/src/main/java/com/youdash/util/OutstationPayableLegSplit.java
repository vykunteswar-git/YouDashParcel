package com.youdash.util;

import com.youdash.entity.OrderEntity;

/**
 * Outstation leg amounts shown to riders and used for commission (pickup / hub
 * / last mile).
 * Prefers persisted quote line items from checkout; coupon discounts are
 * deducted from the hub-to-hub leg only. Falls back to splitting
 * {@link OrderEntity#getTotalAmount()} by leg distances when quote legs are
 * absent (legacy rows).
 */
public record OutstationPayableLegSplit(double pickupAmount, double hubToHubAmount, double lastMileAmount) {

    public static OutstationPayableLegSplit fromOrder(OrderEntity order) {
        if (order == null) {
            return new OutstationPayableLegSplit(0.0, 0.0, 0.0);
        }
        double payable = Math.max(0.0, nz(order.getTotalAmount()));
        if (hasPersistedQuoteLegs(order)) {
            return fromQuoteLegCosts(order);
        }
        if (order.getOutstationDropCost() != null || order.getOutstationPickupCost() != null) {
            OutstationPayableLegSplit split = fromQuoteLegCosts(order);
            if (split.pickupAmount() + split.hubToHubAmount() + split.lastMileAmount() > 0.0) {
                return split;
            }
        }
        return distanceSplitFallback(order, payable);
    }

    private static boolean hasPersistedQuoteLegs(OrderEntity order) {
        return order.getOutstationPickupCost() != null
                && order.getOutstationHubCost() != null
                && order.getOutstationDropCost() != null;
    }

    /**
     * Quote pickup/drop legs stay at checkout freight; coupon is taken only from hub leg.
     */
    private static OutstationPayableLegSplit fromQuoteLegCosts(OrderEntity order) {
        double coupon = round2(Math.max(0.0, nz(order.getCouponAmount())));
        double pickup = round2(nz(order.getOutstationPickupCost()));
        double hub = round2(Math.max(0.0, nz(order.getOutstationHubCost()) - coupon));
        double drop = round2(nz(order.getOutstationDropCost()));
        return new OutstationPayableLegSplit(pickup, hub, drop);
    }

    private static OutstationPayableLegSplit distanceSplitFallback(OrderEntity order, double orderAmount) {
        double coupon = round2(Math.max(0.0, nz(order.getCouponAmount())));
        double preCouponTotal = round2(orderAmount + coupon);

        double pickupKm = Math.max(0.0, nz(order.getPickupDistanceKm()));
        double hubKm = Math.max(0.0, nz(order.getHubDistanceKm()));
        double dropKm = Math.max(0.0, nz(order.getDropDistanceKm()));
        double den = pickupKm + hubKm + dropKm;
        if (den <= 0.0) {
            if (OutstationCodPolicy.isHubToDoor(order)) {
                return new OutstationPayableLegSplit(0.0, 0.0, round2(orderAmount));
            }
            if (OutstationCodPolicy.isDoorToHub(order)) {
                return new OutstationPayableLegSplit(round2(orderAmount), 0.0, 0.0);
            }
            if (OutstationCodPolicy.isDoorToDoor(order)) {
                double drop = order.getOutstationDropCost() != null
                        ? round2(nz(order.getOutstationDropCost()))
                        : round2(orderAmount * 0.5);
                drop = round2(Math.min(orderAmount, Math.max(0.0, drop)));
                double hub = round2(Math.max(0.0,
                        nz(order.getOutstationHubCost()) - coupon));
                double pickup = round2(Math.max(0.0, orderAmount - drop - hub));
                return new OutstationPayableLegSplit(pickup, hub, drop);
            }
            return new OutstationPayableLegSplit(round2(orderAmount), 0.0, 0.0);
        }

        double hubAmount = round2(Math.max(0.0, preCouponTotal * (hubKm / den) - coupon));
        double pickupAmount;
        double dropAmount;

        // Rider pool excludes inter-hub transport; pickup + delivery share pickup + drop km only.
        if (OutstationCodPolicy.isDoorToDoor(order) || OutstationCodPolicy.isHubToDoor(order)) {
            double riderKm = pickupKm + dropKm;
            if (riderKm > 0.0) {
                double riderPool = round2(Math.max(0.0, orderAmount - hubAmount));
                pickupAmount = round2(riderPool * (pickupKm / riderKm));
                dropAmount = round2(riderPool - pickupAmount);
            } else {
                pickupAmount = round2(preCouponTotal * (pickupKm / den));
                dropAmount = round2(orderAmount - pickupAmount - hubAmount);
            }
        } else {
            pickupAmount = round2(preCouponTotal * (pickupKm / den));
            dropAmount = round2(orderAmount - pickupAmount - hubAmount);
        }
        return new OutstationPayableLegSplit(pickupAmount, hubAmount, dropAmount);
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

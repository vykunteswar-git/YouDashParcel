package com.youdash.util;

import com.youdash.entity.OrderEntity;

/**
 * Outstation leg amounts shown to riders and used for commission (pickup / hub
 * / last mile).
 * Prefers persisted quote line items from checkout, scaled by payable vs
 * pre-coupon total; falls back to
 * splitting {@link OrderEntity#getTotalAmount()} by leg distances when quote
 * legs are absent (legacy rows).
 */
public record OutstationPayableLegSplit(double pickupAmount, double hubToHubAmount, double lastMileAmount) {

    public static OutstationPayableLegSplit fromOrder(OrderEntity order) {
        if (order == null) {
            return new OutstationPayableLegSplit(0.0, 0.0, 0.0);
        }
        double payable = Math.max(0.0, nz(order.getTotalAmount()));
        double sf = payableScaleFactor(order);
        if (hasPersistedQuoteLegs(order)) {
            double p = round2(nz(order.getOutstationPickupCost()) * sf);
            double h = round2(nz(order.getOutstationHubCost()) * sf);
            double d = round2(nz(order.getOutstationDropCost()) * sf);
            return new OutstationPayableLegSplit(p, h, d);
        }
        if (order.getOutstationDropCost() != null || order.getOutstationPickupCost() != null) {
            double p = round2(nz(order.getOutstationPickupCost()) * sf);
            double h = round2(nz(order.getOutstationHubCost()) * sf);
            double d = round2(nz(order.getOutstationDropCost()) * sf);
            if (p + h + d > 0.0) {
                return new OutstationPayableLegSplit(p, h, d);
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
     * Ratio of final payable to pre-coupon quote total (subtotal + GST + platform),
     * so leg lines shrink
     * proportionally when a coupon applies.
     */
    static double payableScaleFactor(OrderEntity order) {
        double pre = round2(nz(order.getSubtotal()) + nz(order.getGstAmount()) + nz(order.getPlatformFee()));
        if (pre <= 0.0) {
            return 1.0;
        }
        return round2(Math.max(0.0, nz(order.getTotalAmount())) / pre);
    }

    private static OutstationPayableLegSplit distanceSplitFallback(OrderEntity order, double orderAmount) {
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
                        ? round2(nz(order.getOutstationDropCost()) * payableScaleFactor(order))
                        : round2(orderAmount * 0.5);
                drop = round2(Math.min(orderAmount, Math.max(0.0, drop)));
                return new OutstationPayableLegSplit(round2(Math.max(0.0, orderAmount - drop)), 0.0, drop);
            }
            return new OutstationPayableLegSplit(round2(orderAmount), 0.0, 0.0);
        }
        double pickupAmount = round2(orderAmount * (pickupKm / den));
        double hubAmount = round2(orderAmount * (hubKm / den));
        double dropAmount = round2(orderAmount - pickupAmount - hubAmount);
        // Rider pool excludes inter-hub transport; pickup + delivery share pickup + drop km only.
        if (OutstationCodPolicy.isDoorToDoor(order) || OutstationCodPolicy.isHubToDoor(order)) {
            double riderKm = pickupKm + dropKm;
            if (riderKm > 0.0) {
                double riderPool = round2(orderAmount - hubAmount);
                pickupAmount = round2(riderPool * (pickupKm / riderKm));
                dropAmount = round2(riderPool - pickupAmount);
            }
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

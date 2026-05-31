package com.youdash.realtime;

import com.youdash.model.OrderStatus;

/**
 * Expected next milestone for OUTSTATION rider legs (pickup vs delivery).
 */
public final class OutstationActiveOrderNextStatus {

    private OutstationActiveOrderNextStatus() {
    }

    public static String resolve(OrderStatus current) {
        if (current == null) {
            return null;
        }
        return switch (current) {
            case PICKUP_ASSIGNED, RIDER_ASSIGNED -> OrderStatus.PICKED_UP.name();
            case PICKED_UP -> OrderStatus.AT_ORIGIN_HUB.name();
            case AT_ORIGIN_HUB -> OrderStatus.IN_TRANSIT.name();
            case IN_TRANSIT -> OrderStatus.AT_DESTINATION_HUB.name();
            case OUT_FOR_DELIVERY -> OrderStatus.DELIVERED.name();
            default -> null;
        };
    }
}

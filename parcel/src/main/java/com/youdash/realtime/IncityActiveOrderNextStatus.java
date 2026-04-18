package com.youdash.realtime;

import com.youdash.model.OrderStatus;

/**
 * Expected next {@link OrderStatus} in the INCITY rider happy-path (for UI hints on active-order socket).
 */
public final class IncityActiveOrderNextStatus {

    private IncityActiveOrderNextStatus() {
    }

    /**
     * @return next milestone status name, or null if unknown / terminal / not applicable
     */
    public static String resolve(OrderStatus current) {
        if (current == null) {
            return null;
        }
        return switch (current) {
            case RIDER_ACCEPTED -> OrderStatus.PAYMENT_PENDING.name();
            case PAYMENT_PENDING -> OrderStatus.CONFIRMED.name();
            case CONFIRMED -> OrderStatus.PICKED_UP.name();
            case PICKED_UP -> OrderStatus.IN_TRANSIT.name();
            case IN_TRANSIT -> OrderStatus.DELIVERED.name();
            default -> null;
        };
    }
}

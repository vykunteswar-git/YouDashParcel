package com.youdash.model;

/**
 * Canonical order lifecycle statuses (incity + outstation).
 * Legacy DB/API names are normalized via {@link #fromLegacy(String)}.
 */
public enum OrderStatus {
    BOOKED,
    SEARCHING_RIDER,
    RIDER_ACCEPTED,
    PAYMENT_PENDING,
    RIDER_ASSIGNED,
    PICKED_UP,
    AT_ORIGIN_HUB,
    IN_TRANSIT,
    AT_DESTINATION_HUB,
    OUT_FOR_DELIVERY,
    AWAITING_HUB_COLLECTION,
    DELIVERED,
    COLLECTED,

    DELIVERY_FAILED,
    FAILED_DELIVERY,
    CANCELLED,
    RETURN_INITIATED,
    RETURNED_TO_SENDER,
    RETURNED,
    EXPIRED,
    FAILED;

    /**
     * Maps historical status strings (DB rows, admin UI, mobile caches) to canonical enum values.
     */
    public static OrderStatus fromLegacy(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim().toUpperCase().replace('-', '_');
        try {
            return OrderStatus.valueOf(s);
        } catch (IllegalArgumentException ignored) {
            // fall through
        }
        return switch (s) {
            case "CREATED", "ORDER_CREATED" -> BOOKED;
            case "CONFIRMED", "PICKUP_CONFIRMED" -> RIDER_ASSIGNED;
            case "PARCEL_PICKED_UP" -> PICKED_UP;
            case "ARRIVED_ORIGIN_HUB" -> AT_ORIGIN_HUB;
            case "DISPATCHED_TO_DESTINATION", "DEPARTED_ORIGIN_HUB" -> IN_TRANSIT;
            case "ARRIVED_DESTINATION_HUB", "SORTED_AT_DESTINATION" -> AT_DESTINATION_HUB;
            case "DELIVERY_RIDER_ASSIGNED" -> OUT_FOR_DELIVERY;
            case "READY_FOR_PICKUP" -> AWAITING_HUB_COLLECTION;
            case "COLLECTED_BY_CUSTOMER" -> COLLECTED;
            default -> throw new IllegalArgumentException("Unknown order status: " + raw);
        };
    }
}

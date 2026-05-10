package com.youdash.model;

public enum OrderStatus {
    // ── INCITY flow ───────────────────────────────────────────────────────────
    CREATED,
    SEARCHING_RIDER,
    RIDER_ACCEPTED,
    PAYMENT_PENDING,
    CONFIRMED,
    PICKED_UP,
    OUT_FOR_DELIVERY,
    DELIVERED,

    // ── OUTSTATION flow ───────────────────────────────────────────────────────
    ORDER_CREATED,
    RIDER_ASSIGNED,
    PICKUP_CONFIRMED,
    PARCEL_PICKED_UP,
    ARRIVED_ORIGIN_HUB,
    DISPATCHED_TO_DESTINATION,
    ARRIVED_DESTINATION_HUB,
    DELIVERY_RIDER_ASSIGNED,
    READY_FOR_PICKUP,
    COLLECTED_BY_CUSTOMER,

    // ── Legacy / kept for backward compat ─────────────────────────────────────
    AT_ORIGIN_HUB,
    DEPARTED_ORIGIN_HUB,
    IN_TRANSIT,
    AT_DESTINATION_HUB,
    SORTED_AT_DESTINATION,

    // ── Failure & exception states ────────────────────────────────────────────
    DELIVERY_FAILED,
    FAILED_DELIVERY,
    CANCELLED,
    RETURN_INITIATED,
    RETURNED_TO_SENDER,
    RETURNED,
    EXPIRED,
    FAILED
}

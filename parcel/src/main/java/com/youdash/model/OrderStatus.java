package com.youdash.model;

public enum OrderStatus {
    CREATED,
    SEARCHING_RIDER,
    RIDER_ACCEPTED,
    PAYMENT_PENDING,
    CONFIRMED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED,
    EXPIRED,
    FAILED
}

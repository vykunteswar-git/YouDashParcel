package com.youdash.model;

public enum OrderStatus {
    /** Incity: no rider available yet, or awaiting manual assignment */
    PENDING,
    /** Outstation: created; admin must assign rider */
    PENDING_ASSIGNMENT,
    ASSIGNED,
    PICKED_UP,
    IN_TRANSIT,
    DELIVERED,
    CANCELLED
}

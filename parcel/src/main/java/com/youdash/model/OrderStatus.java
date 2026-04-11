package com.youdash.model;

/**
 * String order lifecycle values persisted on {@link com.youdash.entity.OrderEntity#status}.
 */
public final class OrderStatus {

    private OrderStatus() {
    }

    public static final String CREATED = "CREATED";
    public static final String READY_FOR_ASSIGNMENT = "READY_FOR_ASSIGNMENT";
    public static final String ASSIGNED = "ASSIGNED";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String PICKED_UP = "PICKED_UP";
    /** Legacy / incity line-haul */
    public static final String IN_TRANSIT = "IN_TRANSIT";
    public static final String DELIVERED = "DELIVERED";
    public static final String CANCELLED = "CANCELLED";

    public static final String AT_SOURCE_HUB = "AT_SOURCE_HUB";
    public static final String IN_TRANSIT_TO_DEST_HUB = "IN_TRANSIT_TO_DEST_HUB";
    public static final String AT_DESTINATION_HUB = "AT_DESTINATION_HUB";
    public static final String READY_FOR_DELIVERY = "READY_FOR_DELIVERY";
    public static final String ASSIGNED_TO_DELIVERY_RIDER = "ASSIGNED_TO_DELIVERY_RIDER";
    public static final String OUT_FOR_DELIVERY = "OUT_FOR_DELIVERY";
    public static final String DELIVERED_AT_HUB = "DELIVERED_AT_HUB";

    public static final String FAILED_AT_HUB = "FAILED_AT_HUB";
    public static final String RETURN_INITIATED = "RETURN_INITIATED";
}

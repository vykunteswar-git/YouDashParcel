package com.youdash.notification;

/**
 * Sent in FCM {@code data.type} for client routing (foreground local notification, deep links).
 */
public enum NotificationType {
    ORDER_PLACED_COD,
    PAYMENT_SUCCESS,
    PAYMENT_FAILED,
    PAYMENT_PENDING_REMINDER,
    RIDER_ASSIGNED,
    PICKED_UP,
    AT_SOURCE_HUB,
    IN_TRANSIT_TO_DEST_HUB,
    AT_DESTINATION_HUB,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERED_AT_HUB,
    /** Legacy / generic */
    ORDER_CREATED,
    /** Outstation order waiting for admin to assign rider */
    ADMIN_OUTSTATION_PENDING_ASSIGNMENT,
    /** User notified when order status changes */
    USER_ORDER_STATUS_UPDATE,
    ADMIN_COD_ORDER,
    ADMIN_PAYMENT_SUCCESS,
    ADMIN_PAYMENT_FAILED,
    RIDER_JOB_ASSIGNED
}

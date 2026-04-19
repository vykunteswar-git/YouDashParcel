package com.youdash.notification;

/**
 * Sent in FCM {@code data.type} for client routing (foreground local notification, deep links).
 */
public enum NotificationType {
    ORDER_CREATE_FAILED,
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
    RIDER_JOB_ASSIGNED,
    /** Nearby rider: new INCITY dispatch offer */
    RIDER_NEW_ORDER_REQUEST,
    /** Dispatch round ended for this rider (accepted elsewhere, cancelled, expired, etc.) */
    RIDER_REQUEST_CLOSED,
    /** User: a rider accepted the order (COD confirmed or online awaiting payment) */
    USER_RIDER_ACCEPTED,
    /** User: order ended as cancelled or expired (includes no rider, payment timeout, user cancel) */
    USER_ORDER_CLOSED,

    /** User: rider verified delivery OTP (customer can complete handover flow). */
    USER_OTP_VERIFIED_BY_RIDER,
    /** Rider: customer verified delivery OTP (rider can proceed to complete). */
    RIDER_OTP_VERIFIED_BY_USER,
    /** Rider: online payment captured; job is confirmed — open active order. */
    RIDER_ORDER_PAYMENT_CONFIRMED,
    /** User: coupon saved on order at creation. */
    USER_COUPON_APPLIED,
    /** User: manual pricing request submitted. */
    USER_MANUAL_REQUEST_SUBMITTED,
    /** Admin devices: new manual order request for review. */
    ADMIN_MANUAL_REQUEST_NEW,
    /** Rider: withdrawal approved by admin. */
    RIDER_WITHDRAWAL_APPROVED,
    /** Rider: withdrawal rejected; reservation released. */
    RIDER_WITHDRAWAL_REJECTED,
    /** Rider: admin settled COD collected line for an order. */
    RIDER_COD_SETTLED_ADMIN,
    /** Rider: earning credited to wallet after delivery settlement. */
    RIDER_WALLET_EARNING_CREDITED,
    /** Admin devices: rider requested a wallet withdrawal. */
    ADMIN_RIDER_WITHDRAWAL_REQUESTED
}

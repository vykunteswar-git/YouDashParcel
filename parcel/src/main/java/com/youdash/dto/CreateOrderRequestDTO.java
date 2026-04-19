package com.youdash.dto;

import lombok.Data;

@Data
public class CreateOrderRequestDTO {

    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;

    /** Required; {@code deliveryType} on the order is taken from the category unless {@link #deliveryType} is sent as an override. */
    private Long categoryId;

    private Long vehicleId;
    /**
     * Optional override when the category has no {@code defaultDeliveryType}, or for legacy clients.
     * OUTSTATION: must resolve to a valid OutstationDeliveryType enum name (e.g. DOOR_TO_DOOR).
     */
    private String deliveryType;

    private Long originHubId;
    private Long destinationHubId;

    private Double weight;
    private String paymentType;

    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private String imageUrl;

    /**
     * Optional snapshot from quote {@code vehicleOptions[].perKm} for the chosen vehicle (incity).
     */
    private Double vehiclePricePerKm;

    /**
     * Optional pricing: send all four together, or omit all for server-side pricing.
     * When all four are sent, values are stored as-is (no backend price recalculation); {@code totalAmount} is pre-coupon.
     */
    private Double subtotal;
    private Double gstAmount;
    private Double platformFee;
    /** Pre-coupon total (before {@code couponAmount}). */
    private Double totalAmount;

    /** Discount; payable stored is {@code totalAmount - couponAmount}. Default 0. */
    private Double couponAmount;

    /**
     * Optional promo code (admin-created). When set, discount is computed on the server and overrides
     * {@link #couponAmount} for pricing (legacy clients can still send couponAmount only).
     */
    private String couponCode;
}

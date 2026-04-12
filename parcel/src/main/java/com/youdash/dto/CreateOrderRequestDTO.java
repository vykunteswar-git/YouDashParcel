package com.youdash.dto;

import lombok.Data;

@Data
public class CreateOrderRequestDTO {

    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;

    private Long vehicleId;
    private String deliveryType;

    private Long originHubId;
    private Long destinationHubId;

    private Double weight;
    private String paymentType;

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
}

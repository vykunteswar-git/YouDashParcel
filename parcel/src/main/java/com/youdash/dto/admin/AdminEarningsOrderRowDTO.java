package com.youdash.dto.admin;

import lombok.Data;

@Data
public class AdminEarningsOrderRowDTO {
    private Long orderId;
    private String displayOrderId;
    private String createdAt;
    private String serviceMode;
    private String paymentType;

    /** What the customer paid (subtotal + GST + platformFee - coupon). */
    private Double totalAmount;
    /** Delivery base before GST / platform fee. */
    private Double subtotal;
    private Double gstAmount;
    private Double platformFee;

    /** Average commission % applied across all rider legs for this order. */
    private Double commissionPercent;
    /** Sum of commission taken from all rider legs. */
    private Double commissionAmount;
    /** Sum of rider earning credited across all legs. */
    private Double riderEarning;
    /** commissionAmount + gstAmount + platformFee — total platform net for this order. */
    private Double platformNet;
}

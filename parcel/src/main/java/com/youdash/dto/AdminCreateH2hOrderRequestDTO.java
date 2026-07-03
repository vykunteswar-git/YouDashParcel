package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class AdminCreateH2hOrderRequestDTO {

    private Long originHubId;
    private Long destinationHubId;
    private Double weight;
    /** {@code KG}, {@code G}, or UI labels like {@code Grams (g)} / {@code Kilograms (kg)}. */
    @JsonAlias({"weight_unit", "unit", "weightUnitLabel"})
    private String weightUnit;
    private Integer quantity;
    private Long categoryId;
    private String paymentType;
    /**
     * Payment collection status for hub-to-hub orders.
     * {@code PAID} — freight collected upfront.
     * {@code TO_PAY} — freight to be collected at destination hub.
     * Defaults to {@code PAID} when not provided.
     */
    private String paymentStatus;
    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private String packageContents;
    private Double declaredValue;

    /** When true, use manualFreight/manualGst/manualPlatformFee instead of auto-calculated pricing. */
    private Boolean manualPricing;
    private Double manualFreight;
    private Double manualGst;
    private Double manualPlatformFee;
}

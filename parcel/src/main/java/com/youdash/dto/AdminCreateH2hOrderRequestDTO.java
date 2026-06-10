package com.youdash.dto;

import lombok.Data;

@Data
public class AdminCreateH2hOrderRequestDTO {

    private Long originHubId;
    private Long destinationHubId;
    private Double weight;
    /** {@code KG} or {@code G} — how weight was entered at booking (stored value is always kg). */
    private String weightUnit;
    private Integer quantity;
    private Long categoryId;
    private String paymentType;
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

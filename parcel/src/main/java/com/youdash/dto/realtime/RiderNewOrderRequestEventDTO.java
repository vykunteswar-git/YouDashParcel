package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class RiderNewOrderRequestEventDTO {
    private Long orderId;
    private String customerName;
    private String packageImage;
    private Double weight;
    private String paymentMode;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double distanceKm;
    private Double earningAmount;
    /** Epoch millis when request expires for rider to accept. */
    private Long expiryTimeEpochMs;
}


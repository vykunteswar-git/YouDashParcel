package com.youdash.dto;

import lombok.Data;

@Data
public class FinalPriceRequestDTO {

    private Long originHubId;
    private Long destinationHubId;
    private String deliveryType;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double weight;

    /** Optional — preview discount on outstation final price (same rules as checkout). */
    private String couponCode;
}

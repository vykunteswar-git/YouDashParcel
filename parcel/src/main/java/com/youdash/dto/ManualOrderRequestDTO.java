package com.youdash.dto;

import lombok.Data;

@Data
public class ManualOrderRequestDTO {

    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double weight;
    private String note;
}

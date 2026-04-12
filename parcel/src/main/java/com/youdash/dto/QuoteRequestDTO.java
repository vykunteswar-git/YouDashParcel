package com.youdash.dto;

import lombok.Data;

@Data
public class QuoteRequestDTO {

    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private Double weight;
}

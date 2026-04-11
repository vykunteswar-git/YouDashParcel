package com.youdash.dto;

import lombok.Data;

@Data
public class RoutePreviewRequestDTO {
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
}

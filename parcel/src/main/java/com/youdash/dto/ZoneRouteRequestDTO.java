package com.youdash.dto;

import lombok.Data;

@Data
public class ZoneRouteRequestDTO {

    private Long originZoneId;
    private Long destinationZoneId;
    private Double ratePerKm;
    private Boolean isActive;
}

package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZoneRouteResponseDTO {

    private Long id;
    private Long originZoneId;
    private Long destinationZoneId;
    private Double ratePerKm;
    private Boolean isActive;
}

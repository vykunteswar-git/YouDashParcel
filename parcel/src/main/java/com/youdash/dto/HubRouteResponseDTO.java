package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubRouteResponseDTO {

    private Long id;
    private Long originHubId;
    private Long destinationHubId;
    private Double ratePerKm;
    private Boolean isActive;
}

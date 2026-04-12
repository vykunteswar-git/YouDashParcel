package com.youdash.dto;

import lombok.Data;

@Data
public class HubRouteRequestDTO {

    private Long originHubId;
    private Long destinationHubId;
    private Double ratePerKm;
    private Boolean isActive;
}

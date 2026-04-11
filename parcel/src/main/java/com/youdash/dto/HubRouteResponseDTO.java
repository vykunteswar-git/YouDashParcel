package com.youdash.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HubRouteResponseDTO {

    private Long id;
    private Long sourceHubId;
    private Long destinationHubId;
    private Double pricePerKm;
    private Double fixedPrice;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

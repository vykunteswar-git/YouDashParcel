package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ZonePricingResponseDTO {

    private Long id;
    private Long zoneId;
    private Double pickupRatePerKm;
    private Double deliveryRatePerKm;
    private Double baseFare;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Zone response")
public class ZoneResponseDTO {

    private Long id;
    private String city;
    private String name;
    private Double centerLat;
    private Double centerLng;
    private Double radiusKm;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

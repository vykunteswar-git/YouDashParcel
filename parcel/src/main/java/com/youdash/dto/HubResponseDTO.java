package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Hub response")
public class HubResponseDTO {

    private Long id;
    private String city;
    private String name;
    private Double lat;
    private Double lng;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

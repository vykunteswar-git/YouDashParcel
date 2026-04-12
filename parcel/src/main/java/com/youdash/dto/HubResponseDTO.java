package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubResponseDTO {

    private Long id;
    private String name;
    private String city;
    private Double lat;
    private Double lng;
    private Long zoneId;
    private Boolean isActive;
}

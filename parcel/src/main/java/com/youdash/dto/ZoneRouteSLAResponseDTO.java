package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ZoneRouteSLAResponseDTO {

    private Long id;
    private Long zoneRouteId;
    private String cutoffTime;
    private String deliveryType;
    private String deliveryTime;
    private Integer deliveredWithinHours;
    private Integer priority;
    private Boolean isActive;
}

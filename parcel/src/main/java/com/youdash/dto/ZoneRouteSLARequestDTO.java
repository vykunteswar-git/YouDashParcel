package com.youdash.dto;

import lombok.Data;

@Data
public class ZoneRouteSLARequestDTO {

    private Long zoneRouteId;
    private String cutoffTime;
    private String deliveryType;
    private String deliveryTime;
    private Integer deliveredWithinHours;
    private Integer priority;
    private Boolean isActive;
}

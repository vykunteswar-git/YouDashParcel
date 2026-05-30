package com.youdash.dto;

import lombok.Data;

@Data
public class HubCorridorSlaRequestDTO {

    private Long hubId;
    private Long destinationZoneId;
    private String cutoffTime;
    private String slotLabel;
    private String deliveryType;
    private String deliveryTime;
    private Integer deliveryDayOffset;
    private Integer deliveredWithinHours;
    private Integer priority;
    private Boolean isActive;
}

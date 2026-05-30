package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubCorridorSlaResponseDTO {

    private Long id;
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

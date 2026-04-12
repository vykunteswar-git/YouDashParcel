package com.youdash.dto;

import lombok.Data;

/**
 * Admin create/update body for hub route SLA.
 * {@code deliveryType}: NEXT_DAY or HOURS.
 */
@Data
public class HubRouteSLARequestDTO {

    private Long hubRouteId;

    /** ISO-8601 local time, e.g. "15:00" */
    private String cutoffTime;

    private String deliveryType;

    private String deliveryTime;

    private Integer deliveredWithinHours;

    private Integer priority;

    private Boolean isActive;
}

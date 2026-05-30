package com.youdash.dto;

import lombok.Data;

@Data
public class HubRequestDTO {

    private String name;
    private String city;
    private Double lat;
    private Double lng;
    private Long zoneId;
    /** ISO local time e.g. 14:00 — last intake at this hub for same-day dispatch */
    private String intakeCutoff;
    private Boolean isActive;
}

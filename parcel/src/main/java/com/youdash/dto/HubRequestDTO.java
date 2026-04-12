package com.youdash.dto;

import lombok.Data;

@Data
public class HubRequestDTO {

    private String name;
    private String city;
    private Double lat;
    private Double lng;
    private Long zoneId;
    private Boolean isActive;
}

package com.youdash.dto;

import lombok.Data;

@Data
public class HubAvailabilityDTO {

    private Long id;
    private String city;
    private String name;
    private Double lat;
    private Double lng;
}

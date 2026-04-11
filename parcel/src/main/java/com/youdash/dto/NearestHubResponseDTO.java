package com.youdash.dto;

import lombok.Data;

@Data
public class NearestHubResponseDTO {
    private Long hubId;
    private String city;
    private String name;
    private Double lat;
    private Double lng;
    private Double distanceKm;
}

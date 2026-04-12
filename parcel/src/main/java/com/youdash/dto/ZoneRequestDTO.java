package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.youdash.model.ZoneType;
import lombok.Data;

import java.util.List;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ZoneRequestDTO {

    private String name;
    private String city;
    private Boolean isActive;

    private ZoneType zoneType;

    /** CIRCLE */
    private Double centerLat;
    private Double centerLng;
    private Double radiusKm;

    /** POLYGON — each point [lat, lng] */
    private List<List<Double>> coordinates;
}

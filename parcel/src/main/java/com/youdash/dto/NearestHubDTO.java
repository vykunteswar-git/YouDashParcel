package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Active hub closest to a reference point, with straight-line distance for UX.
 */
@Data
@Schema(description = "Nearest service hub and distance from the reference coordinate")
public class NearestHubDTO {

    private Long id;
    private String city;
    private String name;
    private Double lat;
    private Double lng;

    /** Haversine distance in km from pickup (nearestPickupHub) or drop (nearestDropHub) */
    @Schema(description = "Distance in km from the corresponding pickup or drop point")
    private Double distanceKm;
}

package com.youdash.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ServiceAvailabilityResponseDTO {

    /** INCITY or OUTSTATION */
    private String serviceMode;

    private Long pickupZoneId;
    private Long dropZoneId;
    /** When INCITY, the zone that serves both points */
    private Long servingZoneId;

    private Double straightLineDistanceKm;

    private List<VehicleAvailabilityDTO> vehicles = new ArrayList<>();
    private List<HubAvailabilityDTO> hubs = new ArrayList<>();

    /**
     * True when the route can be served (incity: at least one vehicle after weight filter; outstation: at least one hub).
     */
    private Boolean isServiceable;

    /** OUTSTATION only: closest active hub to pickup coordinates */
    private NearestHubDTO nearestPickupHub;

    /** OUTSTATION only: closest active hub to drop coordinates */
    private NearestHubDTO nearestDropHub;

    /**
     * OUTSTATION only: labeled options; {@link OutstationDeliveryOptionDTO#getType} matches order / pricing
     * ({@code DOOR_TO_DOOR}, {@code DOOR_TO_HUB}, {@code HUB_TO_DOOR}).
     */
    private List<OutstationDeliveryOptionDTO> deliveryOptions = new ArrayList<>();
}

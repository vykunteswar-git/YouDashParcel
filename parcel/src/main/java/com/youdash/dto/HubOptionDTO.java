package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HubOptionDTO {

    private Long id;
    private String name;
    private String city;
    private Double lat;
    private Double lng;
    private Double distanceKm;

    /** Per-hub SLA when paired with the nearest hub on the other side (hub picker UI). */
    private DeliveryPromiseDTO deliveryPromise;
}

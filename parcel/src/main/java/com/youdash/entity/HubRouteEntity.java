package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_hub_routes")
@Data
public class HubRouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_hub_id", nullable = false)
    private Long originHubId;

    @Column(name = "destination_hub_id", nullable = false)
    private Long destinationHubId;

    /** Rate applied to driving/haversine distance between hubs (₹/km) */
    @Column(name = "rate_per_km", nullable = false)
    private Double ratePerKm;

    @Column(name = "is_active")
    private Boolean isActive;
}

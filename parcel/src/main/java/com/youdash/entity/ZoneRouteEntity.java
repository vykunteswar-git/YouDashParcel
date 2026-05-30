package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * Pricing corridor between two zones (e.g. VSKP → Hyderabad).
 * Hub-to-hub rate overrides may still exist on {@link HubRouteEntity}.
 */
@Entity
@Table(name = "youdash_zone_routes",
        uniqueConstraints = @UniqueConstraint(columnNames = {"origin_zone_id", "destination_zone_id"}))
@Data
public class ZoneRouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "origin_zone_id", nullable = false)
    private Long originZoneId;

    @Column(name = "destination_zone_id", nullable = false)
    private Long destinationZoneId;

    @Column(name = "rate_per_km", nullable = false)
    private Double ratePerKm;

    @Column(name = "is_active")
    private Boolean isActive;
}

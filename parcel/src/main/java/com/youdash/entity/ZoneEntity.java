package com.youdash.entity;

import com.youdash.model.ZoneType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_zones")
@Data
public class ZoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "city")
    private String city;

    @Column(name = "is_active")
    private Boolean isActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "zone_type", nullable = false)
    private ZoneType zoneType;

    /** Circle: center latitude */
    @Column(name = "center_lat")
    private Double centerLat;

    /** Circle: center longitude */
    @Column(name = "center_lng")
    private Double centerLng;

    /** Circle: radius in kilometres */
    @Column(name = "radius_km")
    private Double radiusKm;

    /**
     * Polygon: JSON array of [lat, lng] pairs, e.g.
     * [[17.75,83.20],[17.70,83.30],...]
     */
    @Column(name = "coordinates", columnDefinition = "TEXT")
    private String coordinatesJson;
}

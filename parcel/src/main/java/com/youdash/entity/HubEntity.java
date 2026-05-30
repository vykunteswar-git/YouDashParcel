package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_hubs")
@Data
public class HubEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String city;

    @Column(name = "lat", nullable = false)
    private Double lat;

    @Column(name = "lng", nullable = false)
    private Double lng;

    @Column(name = "zone_id")
    private Long zoneId;

    /** Last time parcel can be accepted at this hub for same-day corridor dispatch. */
    @Column(name = "intake_cutoff")
    private java.time.LocalTime intakeCutoff;

    @Column(name = "is_active")
    private Boolean isActive;
}

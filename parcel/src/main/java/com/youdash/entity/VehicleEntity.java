package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_vehicles")
@Data
public class VehicleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;

    @Column(name = "price_per_km")
    private Double pricePerKm;

    @Column(name = "base_fare")
    private Double baseFare;

    @Column(name = "minimum_km")
    private Double minimumKm;

    @Column(name = "max_weight")
    private Double maxWeight;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "image_url")
    private String imageUrl;
}

package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "youdash_zone_pricing", indexes = {
        @Index(name = "idx_zone_pricing_zone", columnList = "zone_id")
})
@Data
public class ZonePricingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private ZoneEntity zone;

    @Column(name = "pickup_rate_per_km", nullable = false)
    private Double pickupRatePerKm;

    @Column(name = "delivery_rate_per_km", nullable = false)
    private Double deliveryRatePerKm;

    @Column(name = "base_fare", nullable = false)
    private Double baseFare;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

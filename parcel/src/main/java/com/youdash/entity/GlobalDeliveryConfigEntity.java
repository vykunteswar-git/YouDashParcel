package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "youdash_global_delivery_config")
@Data
public class GlobalDeliveryConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incity_extension_km", nullable = false)
    private Double incityExtensionKm;

    @Column(name = "incity_extra_rate_per_km", nullable = false)
    private Double incityExtraRatePerKm;

    @Column(name = "base_fare", nullable = false)
    private Double baseFare;

    @Column(name = "minimum_charge", nullable = false)
    private Double minimumCharge;

    @Column(name = "gst_percent")
    private Double gstPercent;

    @Column(name = "platform_fee")
    private Double platformFee;

    /** Outstation: pickup → nearest hub (₹/km). Falls back to incity_extra_rate_per_km if null. */
    @Column(name = "first_mile_rate_per_km")
    private Double firstMileRatePerKm;

    /** Outstation: nearest hub → drop (₹/km). Falls back to incity_extra_rate_per_km if null. */
    @Column(name = "last_mile_rate_per_km")
    private Double lastMileRatePerKm;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

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

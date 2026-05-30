package com.youdash.entity;

import com.youdash.model.OutstationLegType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_outstation_leg_rate_tier")
@Data
public class OutstationLegRateTierEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "leg_type", nullable = false, length = 16)
    private OutstationLegType legType;

    /** Inclusive lower bound (kg). */
    @Column(name = "min_weight_kg", nullable = false)
    private Double minWeightKg;

    /** Exclusive upper bound (kg). */
    @Column(name = "max_weight_kg", nullable = false)
    private Double maxWeightKg;

    @Column(name = "rate_per_km", nullable = false)
    private Double ratePerKm;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;

    @PrePersist
    void prePersist() {
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }
}

package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_weight_cost_slab")
@Data
public class WeightCostSlabEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Inclusive lower bound (kg). */
    @Column(name = "min_weight_kg", nullable = false)
    private Double minWeightKg;

    /** Exclusive upper bound (kg). */
    @Column(name = "max_weight_kg", nullable = false)
    private Double maxWeightKg;

    /** Flat charge applied for any parcel in this weight range. */
    @Column(name = "flat_cost", nullable = false)
    private Double flatCost;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;

    @PrePersist
    void prePersist() {
        if (isActive == null) isActive = true;
        if (sortOrder == null) sortOrder = 0;
    }
}

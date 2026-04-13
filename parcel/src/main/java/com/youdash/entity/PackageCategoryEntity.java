package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_package_categories")
@Data
public class PackageCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Emoji or short label shown in UI, e.g. 📦 */
    @Column(name = "emoji", length = 64)
    private String emoji;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_active")
    private Boolean isActive;

    /**
     * Default delivery label stored on the order and returned in order APIs.
     * INCITY: e.g. {@code STANDARD}. OUTSTATION: e.g. {@code DOOR_TO_DOOR}.
     * When null, create-order may still send {@code deliveryType} as an override.
     */
    @Column(name = "default_delivery_type", length = 64)
    private String defaultDeliveryType;
}

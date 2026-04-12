package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "youdash_hub_route_sla")
@Data
public class HubRouteSlaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hub_route_id", nullable = false)
    private Long hubRouteId;

    @Column(name = "cutoff_time")
    private LocalTime cutoffTime;

    /** NEXT_DAY or HOURS */
    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    /** Used when deliveryType is NEXT_DAY */
    @Column(name = "delivery_time")
    private LocalTime deliveryTime;

    /** Used when deliveryType is HOURS */
    @Column(name = "delivered_within_hours")
    private Integer deliveredWithinHours;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.time.LocalTime;

/**
 * Per-origin-hub delivery promise to a destination zone (e.g. Vizag Hub A → Hyderabad zone).
 * Price still comes from {@link ZoneRouteEntity}; this only overrides timing for that hub.
 */
@Entity
@Table(name = "youdash_hub_corridor_sla",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hub_id", "destination_zone_id", "priority"}))
@Data
public class HubCorridorSlaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hub_id", nullable = false)
    private Long hubId;

    @Column(name = "destination_zone_id", nullable = false)
    private Long destinationZoneId;

    /** Last handover / bus departure time for this slot (required for dispatch slots). */
    @Column(name = "cutoff_time")
    private LocalTime cutoffTime;

    @Column(name = "slot_label", length = 80)
    private String slotLabel;

    @Column(name = "delivery_type", nullable = false, length = 20)
    private String deliveryType;

    @Column(name = "delivery_time")
    private LocalTime deliveryTime;

    /** Calendar days after handover date for delivery (0 = same day, 1 = next day). */
    @Column(name = "delivery_day_offset")
    private Integer deliveryDayOffset;

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

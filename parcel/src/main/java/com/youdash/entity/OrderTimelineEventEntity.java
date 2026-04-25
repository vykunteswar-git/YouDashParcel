package com.youdash.entity;

import com.youdash.model.OrderStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "youdash_order_timeline_events",
        indexes = {
                @Index(name = "idx_order_timeline_order", columnList = "order_id"),
                @Index(name = "idx_order_timeline_status", columnList = "status"),
                @Index(name = "idx_order_timeline_created_at", columnList = "created_at")
        })
@Data
public class OrderTimelineEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private Integer eventVersion;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "rider_id")
    private Long riderId;

    @Column(name = "notes", length = 512)
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (eventVersion == null || eventVersion <= 0) {
            eventVersion = 1;
        }
    }
}

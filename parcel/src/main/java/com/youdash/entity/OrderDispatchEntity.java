package com.youdash.entity;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(
        name = "youdash_order_dispatches",
        indexes = {
                @Index(name = "idx_dispatch_order", columnList = "order_id"),
                @Index(name = "idx_dispatch_rider", columnList = "rider_id"),
                @Index(name = "idx_dispatch_order_rider", columnList = "order_id,rider_id", unique = true)
        })
@Data
public class OrderDispatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    /** NOTIFIED | REJECTED | CLOSED */
    @Column(name = "status", length = 16, nullable = false)
    private String status;

    @Column(name = "round_num")
    private Integer roundNum;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null || status.isBlank()) {
            status = "NOTIFIED";
        }
    }
}


package com.youdash.entity;

import com.youdash.model.OrderAssignmentRole;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "youdash_order_assignments",
        indexes = {
                @Index(name = "idx_order_assignments_order", columnList = "order_id"),
                @Index(name = "idx_order_assignments_rider", columnList = "rider_id"),
                @Index(name = "idx_order_assignments_order_role", columnList = "order_id,assignment_role")
        })
@Data
public class OrderAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 16)
    private OrderAssignmentRole assignmentRole;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @PrePersist
    void prePersist() {
        if (assignedAt == null) {
            assignedAt = Instant.now();
        }
        if (isActive == null) {
            isActive = Boolean.TRUE;
        }
    }
}

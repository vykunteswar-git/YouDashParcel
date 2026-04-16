package com.youdash.entity.wallet;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_fin_audit_logs", indexes = {
        @Index(name = "idx_fin_audit_created", columnList = "created_at"),
        @Index(name = "idx_fin_audit_actor", columnList = "actor_type,actor_id")
})
@Data
public class FinAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "actor_type", length = 16)
    private String actorType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "payload_json", columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

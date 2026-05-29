package com.youdash.entity.wallet;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_cod_deposits", indexes = {
        @Index(name = "idx_cod_deposit_rider", columnList = "rider_id"),
        @Index(name = "idx_cod_deposit_created", columnList = "created_at")
})
@Data
public class CodDepositEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "hub_id")
    private Long hubId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

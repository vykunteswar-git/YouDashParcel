package com.youdash.entity.wallet;

import java.time.Instant;

import com.youdash.model.wallet.WithdrawalStatus;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_rider_withdrawals", indexes = {
        @Index(name = "idx_withdraw_rider_created", columnList = "rider_id,created_at"),
        @Index(name = "idx_withdraw_status", columnList = "status")
})
@Data
public class RiderWithdrawalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WithdrawalStatus status;

    @Column(name = "bank_account_name", length = 128)
    private String bankAccountName;

    @Column(name = "bank_account_number", length = 64)
    private String bankAccountNumber;

    @Column(name = "bank_ifsc", length = 32)
    private String bankIfsc;

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
        if (status == null) {
            status = WithdrawalStatus.PENDING;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

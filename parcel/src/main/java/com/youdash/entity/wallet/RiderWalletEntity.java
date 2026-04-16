package com.youdash.entity.wallet;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_rider_wallets", indexes = {
        @Index(name = "idx_rider_wallet_rider", columnList = "rider_id", unique = true)
})
@Data
public class RiderWalletEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false, unique = true)
    private Long riderId;

    /** Cash ledger balance (credits/debits). */
    @Column(name = "current_balance", nullable = false)
    private Double currentBalance;

    /** Lifetime credited rider earnings (net of commission, includes surge). */
    @Column(name = "total_earnings", nullable = false)
    private Double totalEarnings;

    /** Lifetime completed withdrawals. */
    @Column(name = "total_withdrawn", nullable = false)
    private Double totalWithdrawn;

    /** COD cash held by rider pending settlement to admin. */
    @Column(name = "cod_pending_amount", nullable = false)
    private Double codPendingAmount;

    /** Funds reserved for pending withdrawal requests. */
    @Column(name = "withdrawal_pending_amount", nullable = false)
    private Double withdrawalPendingAmount;

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
        if (currentBalance == null) {
            currentBalance = 0.0;
        }
        if (totalEarnings == null) {
            totalEarnings = 0.0;
        }
        if (totalWithdrawn == null) {
            totalWithdrawn = 0.0;
        }
        if (codPendingAmount == null) {
            codPendingAmount = 0.0;
        }
        if (withdrawalPendingAmount == null) {
            withdrawalPendingAmount = 0.0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

package com.youdash.entity.wallet;

import java.time.Instant;

import com.youdash.model.wallet.CodCollectionMode;
import com.youdash.model.wallet.CodSettlementStatus;
import jakarta.persistence.*;
import lombok.Data;

/**
 * One row per order for idempotent rider financial settlement + COD tracking.
 */
@Entity
@Table(name = "youdash_order_rider_financials", indexes = {
        @Index(name = "idx_orf_order", columnList = "order_id", unique = true),
        @Index(name = "idx_orf_rider", columnList = "rider_id")
})
@Data
public class OrderRiderFinancialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "order_amount", nullable = false)
    private Double orderAmount;

    @Column(name = "commission_percent_applied")
    private Double commissionPercentApplied;

    @Column(name = "commission_amount", nullable = false)
    private Double commissionAmount;

    @Column(name = "surge_bonus_amount", nullable = false)
    private Double surgeBonusAmount;

    @Column(name = "rider_earning_amount", nullable = false)
    private Double riderEarningAmount;

    @Column(name = "cod_collected_amount")
    private Double codCollectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "cod_collection_mode", length = 8)
    private CodCollectionMode codCollectionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cod_settlement_status", length = 16)
    private CodSettlementStatus codSettlementStatus;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (codSettlementStatus == null) {
            codSettlementStatus = CodSettlementStatus.PENDING;
        }
    }
}

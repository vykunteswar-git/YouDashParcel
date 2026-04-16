package com.youdash.entity.wallet;

import java.time.Instant;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_rider_commission_config")
@Data
public class RiderCommissionConfigEntity {

    @Id
    private Long id;

    /** Percent of order_amount for ONLINE settlements (0-100). */
    @Column(name = "online_commission_percent")
    private Double onlineCommissionPercent;

    /** Percent of order_amount for COD when rider collects via CASH (0-100). */
    @Column(name = "cod_cash_commission_percent")
    private Double codCashCommissionPercent;

    /** Percent of order_amount for COD when rider collects via QR (0-100). */
    @Column(name = "cod_qr_commission_percent")
    private Double codQrCommissionPercent;

    /** Flat surge bonus added to rider earning (same currency as order). */
    @Column(name = "peak_surge_bonus_flat")
    private Double peakSurgeBonusFlat;

    /** Flat base component for rider earning per delivered order. */
    @Column(name = "base_fee")
    private Double baseFee;

    /** Per-km component for rider earning (multiplied by {@code OrderEntity.distanceKm}). */
    @Column(name = "per_km_rate")
    private Double perKmRate;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

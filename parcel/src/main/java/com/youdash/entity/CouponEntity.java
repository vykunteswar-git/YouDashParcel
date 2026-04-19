package com.youdash.entity;

import java.time.Instant;

import com.youdash.model.CouponDiscountType;
import com.youdash.model.ServiceMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "youdash_coupons")
@Data
public class CouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "description", length = 512)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 16)
    private CouponDiscountType discountType;

    /** Percent (0–100) or flat currency discount, depending on {@link #discountType}. */
    @Column(name = "discount_value", nullable = false)
    private Double discountValue;

    @Column(name = "max_discount_amount")
    private Double maxDiscountAmount;

    @Column(name = "min_order_amount")
    private Double minOrderAmount;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_to", nullable = false)
    private Instant validTo;

    @Column(name = "max_redemptions_total")
    private Integer maxRedemptionsTotal;

    @Column(name = "redemption_count", nullable = false)
    private Integer redemptionCount = 0;

    @Column(name = "max_redemptions_per_user", nullable = false)
    private Integer maxRedemptionsPerUser = 1;

    /** When set, coupon applies only to this service mode; when null, all modes. */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode", length = 16)
    private ServiceMode serviceMode;

    @Column(name = "is_active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant n = Instant.now();
        if (createdAt == null) {
            createdAt = n;
        }
        updatedAt = n;
        if (redemptionCount == null) {
            redemptionCount = 0;
        }
        if (maxRedemptionsPerUser == null) {
            maxRedemptionsPerUser = 1;
        }
        if (active == null) {
            active = true;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

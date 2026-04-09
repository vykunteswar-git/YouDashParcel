package com.youdash.entity;

import com.youdash.pricing.DeliveryScope;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "youdash_delivery_type_rates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"delivery_type_id", "scope"}))
@Data
public class DeliveryTypeRateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_type_id", nullable = false)
    private DeliveryTypeEntity deliveryType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false)
    private DeliveryScope scope;

    @Column(name = "fee", precision = 12, scale = 2, nullable = false)
    private BigDecimal fee;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


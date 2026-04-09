package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "youdash_gst_config")
@Data
public class GstConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cgst_percent", precision = 6, scale = 2, nullable = false)
    private BigDecimal cgstPercent;

    @Column(name = "sgst_percent", precision = 6, scale = 2, nullable = false)
    private BigDecimal sgstPercent;

    @Column(name = "active", nullable = false)
    private Boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


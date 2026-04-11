package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "youdash_hub_routes", indexes = {
        @Index(name = "idx_hub_route_src_dst", columnList = "source_hub_id,destination_hub_id")
})
@Data
public class HubRouteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "source_hub_id", nullable = false)
    private HubEntity sourceHub;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_hub_id", nullable = false)
    private HubEntity destinationHub;

    @Column(name = "price_per_km")
    private Double pricePerKm;

    @Column(name = "fixed_price")
    private Double fixedPrice;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "youdash_banners")
@Data
public class BannerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", length = 128)
    private String title;

    @Column(name = "subtitle", length = 255)
    private String subtitle;

    @Column(name = "image_url", nullable = false, length = 1024)
    private String imageUrl;

    @Column(name = "redirect_url", length = 1024)
    private String redirectUrl;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /** Optional publish window start (UTC). */
    @Column(name = "starts_at")
    private Instant startsAt;

    /** Optional publish window end (UTC). */
    @Column(name = "ends_at")
    private Instant endsAt;

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
        if (isActive == null) {
            isActive = true;
        }
        if (sortOrder == null) {
            sortOrder = 0;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

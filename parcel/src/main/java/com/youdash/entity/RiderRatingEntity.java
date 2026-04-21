package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "youdash_rider_ratings", indexes = {
        @Index(name = "idx_rider_rating_rider_created", columnList = "rider_id,created_at"),
        @Index(name = "idx_rider_rating_user_created", columnList = "user_id,created_at")
})
@Data
public class RiderRatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 1..5 */
    @Column(name = "stars", nullable = false)
    private Integer stars;

    /** Comma-separated compliment slugs, e.g. SAFE_DRIVER,ON_TIME */
    @Column(name = "compliments_csv", length = 512)
    private String complimentsCsv;

    @Column(name = "comment", length = 512)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}

package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "rider_location_history", indexes = {
        @Index(name = "idx_rlh_order_ts", columnList = "order_id, ts DESC"),
        @Index(name = "idx_rlh_rider_ts", columnList = "rider_id, ts DESC")
})
public class RiderLocationHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(nullable = false)
    private Double lat;

    @Column(nullable = false)
    private Double lng;

    @Column(nullable = false)
    private Instant ts;

    private Double heading;
}

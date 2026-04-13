package com.youdash.entity;

import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "youdash_orders")
@Data
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "package_category_id")
    private Long packageCategoryId;

    @Column(name = "sender_name", length = 128)
    private String senderName;

    @Column(name = "sender_phone", length = 32)
    private String senderPhone;

    @Column(name = "receiver_name", length = 128)
    private String receiverName;

    @Column(name = "receiver_phone", length = 32)
    private String receiverPhone;

    @Column(name = "pickup_lat", nullable = false)
    private Double pickupLat;

    @Column(name = "pickup_lng", nullable = false)
    private Double pickupLng;

    @Column(name = "drop_lat", nullable = false)
    private Double dropLat;

    @Column(name = "drop_lng", nullable = false)
    private Double dropLng;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_mode", nullable = false)
    private ServiceMode serviceMode;

    @Column(name = "vehicle_id")
    private Long vehicleId;

    /** INCITY: e.g. STANDARD | OUTSTATION: DOOR_TO_DOOR, etc. */
    @Column(name = "delivery_type")
    private String deliveryType;

    @Column(name = "origin_hub_id")
    private Long originHubId;

    @Column(name = "destination_hub_id")
    private Long destinationHubId;

    @Column(name = "weight", nullable = false)
    private Double weight;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "rider_id")
    private Long riderId;

    @Column(name = "pickup_distance_km")
    private Double pickupDistanceKm;

    @Column(name = "hub_distance_km")
    private Double hubDistanceKm;

    @Column(name = "drop_distance_km")
    private Double dropDistanceKm;

    @Column(name = "subtotal")
    private Double subtotal;

    @Column(name = "gst_amount")
    private Double gstAmount;

    @Column(name = "platform_fee")
    private Double platformFee;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "coupon_amount")
    private Double couponAmount;

    /** Snapshot of vehicle {@code pricePerKm} from quote (incity), when applicable. */
    @Column(name = "vehicle_price_per_km")
    private Double vehiclePricePerKm;

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
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

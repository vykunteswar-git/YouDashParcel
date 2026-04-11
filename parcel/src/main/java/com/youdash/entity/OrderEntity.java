package com.youdash.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import jakarta.persistence.*;

import lombok.Data;

@Entity
@Table(name = "youdash_orders")
@Data
public class OrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", unique = true)
    private String orderId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "pickup_address")
    private String pickupAddress;

    @Column(name = "delivery_address")
    private String deliveryAddress;

    @Column(name = "pickup_lat")
    private Double pickupLat;

    @Column(name = "pickup_lng")
    private Double pickupLng;

    @Column(name = "delivery_lat")
    private Double deliveryLat;

    @Column(name = "delivery_lng")
    private Double deliveryLng;

    @Column(name = "sender_name")
    private String senderName;

    @Column(name = "sender_phone")
    private String senderPhone;

    @Column(name = "receiver_name")
    private String receiverName;

    @Column(name = "receiver_phone")
    private String receiverPhone;

    @Column(name = "package_category_id")
    private Long packageCategoryId;

    @Column(name = "description")
    private String description;

    @Column(name = "weight")
    private Double weight;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "vehicle_type_id")
    private Long vehicleTypeId;

    @Column(name = "distance_km")
    private Double distanceKm;

    @Column(name = "total_amount")
    private Double totalAmount;

    @Column(name = "base_amount", precision = 12, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "platform_fee", precision = 12, scale = 2)
    private BigDecimal platformFee;

    @Column(name = "delivery_fee", precision = 12, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "discount_amount", precision = 12, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "gst_amount", precision = 12, scale = 2)
    private BigDecimal gstAmount;

    @Column(name = "price_per_km_used", precision = 12, scale = 2)
    private BigDecimal pricePerKmUsed;

    @Column(name = "payment_type")
    private String paymentType;

    @Column(name = "payment_status")
    private String paymentStatus;

    @Column(name = "razorpay_order_id")
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id")
    private String razorpayPaymentId;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_created_at")
    private LocalDateTime paymentCreatedAt;

    @Column(name = "payment_updated_at")
    private LocalDateTime paymentUpdatedAt;

    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Column(name = "time_slot")
    private String timeSlot;

    @Column(name = "status")
    private String status;

    /** Pickup / first-mile rider */
    @Column(name = "rider_id")
    private Long riderId;

    /** Last-mile rider (outstation hub-to-door) */
    @Column(name = "delivery_rider_id")
    private Long deliveryRiderId;

    /** INCITY | DOOR_TO_DOOR | HUB_TO_HUB | DOOR_TO_HUB | HUB_TO_DOOR */
    @Column(name = "fulfillment_type", length = 32)
    private String fulfillmentType;

    @Column(name = "package_items")
    private String packageItems;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

package com.youdash.entity;

import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;
import com.youdash.model.wallet.CodSettlementStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(
        name = "youdash_orders",
        indexes = {
                @Index(name = "idx_orders_status", columnList = "status"),
                @Index(name = "idx_orders_search_expires_at", columnList = "search_expires_at"),
                @Index(name = "idx_orders_payment_due_at", columnList = "payment_due_at")
        })
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

    @Column(name = "image_url")
    private String imageUrl;

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

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "search_expires_at")
    private Instant searchExpiresAt;

    @Column(name = "payment_due_at")
    private Instant paymentDueAt;

    @Column(name = "cancel_reason", length = 64)
    private String cancelReason;

    @Column(name = "pickup_distance_km")
    private Double pickupDistanceKm;

    @Column(name = "hub_distance_km")
    private Double hubDistanceKm;

    @Column(name = "drop_distance_km")
    private Double dropDistanceKm;

    /** Total distance (km) used for rider earning; may mirror leg sums or route distance. */
    @Column(name = "distance_km")
    private Double distanceKm;

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

    /** Customer-facing reference, e.g. {@code YP-1231735123456789}. */
    @Column(name = "display_order_id", unique = true, length = 64)
    private String displayOrderId;

    /** UNPAID | PAID | FAILED (Razorpay / online). */
    @Column(name = "payment_status", length = 32)
    private String paymentStatus;

    @Column(name = "razorpay_order_id", length = 255)
    private String razorpayOrderId;

    @Column(name = "razorpay_payment_id", length = 255)
    private String razorpayPaymentId;

    @Column(name = "payment_method", length = 32)
    private String paymentMethod;

    @Column(name = "payment_created_at")
    private Instant paymentCreatedAt;

    @Column(name = "payment_updated_at")
    private Instant paymentUpdatedAt;

    @Column(name = "cod_collected_amount")
    private Double codCollectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "cod_collection_mode", length = 8)
    private CodCollectionMode codCollectionMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "cod_settlement_status", length = 16)
    private CodSettlementStatus codSettlementStatus;

    /** Handover OTP; generated once when rider accepts (INCITY). */
    @Column(name = "delivery_otp", length = 6)
    private String deliveryOtp;

    /** True after successful {@code POST /orders/{id}/verify-otp} while {@code IN_TRANSIT}. */
    @Column(name = "is_otp_verified")
    private Boolean isOtpVerified;

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
        if (isOtpVerified == null) {
            isOtpVerified = false;
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}

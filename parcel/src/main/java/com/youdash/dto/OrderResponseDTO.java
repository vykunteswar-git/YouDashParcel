package com.youdash.dto;

import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;
import com.youdash.model.wallet.CodSettlementStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

@Data
@Builder
public class OrderResponseDTO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private String pickupAddress;
    private String pickupTag;
    private String pickupDoorNo;
    private String pickupLandmark;
    private String dropAddress;
    private String dropTag;
    private String dropDoorNo;
    private String dropLandmark;
    private String imageUrl;
    private String packageContents;
    private Double declaredValue;
    private Integer pieceCount;
    private Boolean isFragile;
    private Boolean containsLiquid;
    private Boolean containsBattery;
    private Boolean prohibitedItemsAccepted;
    private Boolean parcelDeclarationAccepted;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private ServiceMode serviceMode;
    /**
     * Vehicle shown for this job: assigned rider's catalog vehicle when set, otherwise the order's
     * booked vehicle (INCITY). May be null if neither is set.
     */
    private Long vehicleId;
    /** Display name from {@code youdash_vehicles} for {@link #vehicleId} when resolved. */
    private String vehicleName;
    /** Vehicle image from catalog for {@link #vehicleId} when resolved. */
    private String vehicleImageUrl;
    /** Assigned rider's registration / plate when a rider is set. */
    private String vehicleNumber;
    private String deliveryType;
    /** Rider-facing leg discriminator for operational screens: INCITY | PICKUP | DROP. */
    private String legTypeForRider;
    /** Amount allocated to the rider's current leg (outstation), when available. */
    private Double legAmountForRider;
    /** Outstation split of payable total for first-mile pickup leg. */
    private Double pickupAmount;
    /** Outstation split of payable total for middle inter-hub leg. */
    private Double hubToHubAmount;
    /** Outstation split of payable total for last-mile delivery leg. */
    private Double lastMileAmount;
    private Long originHubId;
    private Long destinationHubId;
    private String originHubName;
    private String originHubCity;
    private Double originHubLat;
    private Double originHubLng;
    private String destinationHubName;
    private String destinationHubCity;
    private Double destinationHubLat;
    private Double destinationHubLng;
    private Double weight;
    private Double distanceKm;
    private PaymentType paymentType;
    private OrderStatus status;
    /** Next valid statuses for admin/rider UI actions from the current state. */
    private Set<OrderStatus> allowedNextStatuses;
    /** Curated status list for admin dropdown (reduced options for operations UX). */
    private Set<OrderStatus> adminSelectableNextStatuses;
    private Long riderId;
    private Long pickupRiderId;
    private Long deliveryRiderId;
    /** Populated when {@code riderId} is set (rider profile name / phone). */
    private String riderName;
    private String riderPhone;
    /**
     * Customer: visible from post-accept job states until verified. Rider APIs: only in {@code IN_TRANSIT}.
     * Cleared after OTP verification.
     */
    private String deliveryOtp;
    private Boolean isOtpVerified;
    private String pickupOtp;
    /** DOOR_TO_HUB: OTP for receiver collection at destination hub (READY_FOR_PICKUP). */
    private String hubCollectionOtp;
    private String deliveryOtpGeneratedAt;
    private Integer deliveryOtpAttempts;
    private Double subtotal;
    private Double gstAmount;
    private Double platformFee;
    /** Payable amount after coupon (pre-coupon total minus couponAmount). */
    private Double totalAmount;
    private Double couponAmount;
    /** When a promo code was applied at checkout (users see this on order detail/history). */
    private String appliedCouponCode;
    /**
     * Rider APIs: estimated or settled earning for this order (same formula as wallet settlement;
     * before delivery this is an estimate from distance + commission config).
     */
    private Double earnedAmount;
    private Double vehiclePricePerKm;
    /** Public reference for payments (e.g. YP-…). */
    private String displayOrderId;
    private String paymentStatus;
    private String razorpayOrderId;
    private Double codCollectedAmount;
    private CodCollectionMode codCollectionMode;
    private CodSettlementStatus codSettlementStatus;
    /** True when COD was already collected earlier in the workflow. */
    private Boolean codAlreadyCollected;
    /** Rider UI helper: false when COD is already collected by another stage. */
    private Boolean showCollectCodAction;
    /** True when user can rate rider for this delivered order. */
    private Boolean canRateRider;
    /** True when rider rating already exists for this order. */
    private Boolean riderRatingSubmitted;
    /** User-provided rider stars for this order (1..5), when rated. */
    private Integer riderRating;
    private String estimatedDeliveryTime;
    private Boolean cutoffApplied;
    private List<OrderTimelineEventDTO> timelineEvents;
    private String createdAt;
}

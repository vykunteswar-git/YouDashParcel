package com.youdash.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import lombok.Data;

@Data
public class OrderResponseDTO {

    private Long id;
    private String orderId;
    private Long userId;

    private String pickupAddress;
    private String deliveryAddress;

    private Double pickupLat;
    private Double pickupLng;

    private Double deliveryLat;
    private Double deliveryLng;

    private String senderName;
    private String senderPhone;

    private String receiverName;
    private String receiverPhone;

    private Long packageCategoryId;
    private String packageCategoryName;

    private String description;
    private Double weight;
    private String imageUrl;

    private Long vehicleTypeId;
    private String vehicleName;

    private Double distanceKm;
    private Double totalAmount;

    private Double baseAmount;
    private Double platformFee;
    private Double deliveryFee;
    private Double discountAmount;
    private Double gstAmount;

    private Double pricePerKmUsed;

    private String paymentType;
    private String paymentStatus;
    private String paymentMethod;
    private LocalDateTime paymentCreatedAt;
    private LocalDateTime paymentUpdatedAt;
    private String razorpayOrderId;
    private String razorpayPaymentId;

    private String status;
    private Long riderId;
    private Long deliveryRiderId;
    /** INCITY | DOOR_TO_DOOR | DOOR_TO_HUB | HUB_TO_DOOR (legacy rows may show HUB_TO_HUB) */
    private String fulfillmentType;

    private LocalDate scheduledDate;
    private String timeSlot;
    
    private LocalDateTime createdAt;
    
    private java.util.List<Long> packageItemIds;
    private java.util.List<String> packageItemNames;
}

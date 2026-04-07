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

    private String paymentType;
    private String paymentStatus;

    private String status;
    private Long riderId;

    private LocalDate scheduledDate;
    private String timeSlot;
    
    private LocalDateTime createdAt;
    
    private java.util.List<Long> packageItemIds;
    private java.util.List<String> packageItemNames;
}

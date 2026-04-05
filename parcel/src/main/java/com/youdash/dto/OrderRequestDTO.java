package com.youdash.dto;

import java.time.LocalDate;

import lombok.Data;

@Data
public class OrderRequestDTO {

    private Long userId;

    private String pickupAddress;
    private String deliveryAddress;

    private String receiverName;
    private String receiverPhone;

    private String category;
    private String description;
    private Double weight;
    private String imageUrl;

    private String vehicleType;

    private Double distanceKm;
    private Double totalAmount;

    private String paymentType;
    private String paymentStatus;

    private LocalDate scheduledDate;
    private String timeSlot;
}

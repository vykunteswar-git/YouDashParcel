package com.youdash.dto;

import java.time.LocalDate;
import lombok.Data;

@Data
public class OrderRequestDTO {

    private Long userId;

    private String pickupAddress;
    private String deliveryAddress;

    private Double pickupLat;
    private Double pickupLng;

    private Double deliveryLat;
    private Double deliveryLng;

    private String senderName;
    private String senderPhone;

    private String deliveryType;

    private String receiverName;
    private String receiverPhone;

    private Long packageCategoryId;
    private String description;
    private Double weight;
    private String imageUrl;

    private Long vehicleTypeId;

    private Double distanceKm;

    private String paymentType;

    private LocalDate scheduledDate;
    private String timeSlot;

    private java.util.List<Long> packageItemIds;
}

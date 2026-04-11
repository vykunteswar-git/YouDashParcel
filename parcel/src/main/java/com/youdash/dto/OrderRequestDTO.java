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

    private String receiverName;
    private String receiverPhone;

    private Long packageCategoryId;
    private String description;
    private Double weight;
    private String imageUrl;

    /** Required for INCITY orders; omitted for OUTSTATION */
    private Long vehicleTypeId;

    /**
     * Outstation only: {@code DOOR_TO_DOOR}, {@code HUB_TO_HUB}, {@code DOOR_TO_HUB}, or {@code HUB_TO_DOOR}.
     * Defaults to {@code HUB_TO_DOOR} when omitted.
     */
    private String deliveryOption;

    private Double distanceKm;

    private String paymentType;

    private LocalDate scheduledDate;
    private String timeSlot;

    private java.util.List<Long> packageItemIds;
}

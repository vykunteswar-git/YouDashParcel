package com.youdash.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OrderTrackingDTO {

    private Long orderId;
    private String orderPublicId;
    private String status;

    private Long riderId;
    private String riderName;
    private String riderPhone;
    private Double riderLat;
    private Double riderLng;

    private LocalDateTime updatedAt;
}

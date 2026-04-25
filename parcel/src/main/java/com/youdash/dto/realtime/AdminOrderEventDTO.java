package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class AdminOrderEventDTO {
    private String event;
    private String eventType;
    private Integer eventVersion;
    private Long tsEpochMs;
    private String source;

    private Long orderId;
    private Long userId;
    private Long riderId;
    private String serviceMode;
    private String status;
    private String paymentType;
    private Double totalAmount;
}

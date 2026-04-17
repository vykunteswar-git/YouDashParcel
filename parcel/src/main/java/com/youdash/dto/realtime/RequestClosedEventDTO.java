package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class RequestClosedEventDTO {
    private Long orderId;
    private String reason; // accepted | expired | cancelled
    private Long acceptedRiderId;
}


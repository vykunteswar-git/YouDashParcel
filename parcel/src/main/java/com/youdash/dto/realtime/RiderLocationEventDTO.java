package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class RiderLocationEventDTO {
    private Long orderId;
    private Long riderId;
    private Double lat;
    private Double lng;
    /** Epoch millis; client can ignore and use arrival time if needed. */
    private Long ts;
}


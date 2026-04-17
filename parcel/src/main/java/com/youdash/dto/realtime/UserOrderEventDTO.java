package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class UserOrderEventDTO {
    private Long orderId;
    private String event; // searching_rider | rider_found | payment_required | confirmed | cancelled
    private String status;
    private Long paymentDueAtEpochMs;
    private Long riderId;
}


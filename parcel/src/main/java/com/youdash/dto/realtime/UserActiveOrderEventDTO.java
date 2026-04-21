package com.youdash.dto.realtime;

import lombok.Data;

/**
 * Pushed to /topic/users/{userId}/active-order to keep order status and ETA in sync.
 */
@Data
public class UserActiveOrderEventDTO {
    private String event; // snapshot | status_updated | eta_updated | released
    private Boolean hasActiveOrder;
    private Long orderId;
    private String status;
    private Integer etaSeconds;
    private Double distanceToDropKm;
    private Long riderId;
}

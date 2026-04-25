package com.youdash.dto.realtime;

import lombok.Data;

/**
 * Pushed to /topic/users/{userId}/active-order to keep order status and ETA in sync.
 */
@Data
public class UserActiveOrderEventDTO {
    private String event; // snapshot | status_updated | eta_updated | released
    private Integer eventVersion;
    private Long tsEpochMs;
    private String source;
    private Boolean hasActiveOrder;
    private Long orderId;
    private String status;
    private String serviceMode;
    private String stage;
    private Integer etaSeconds;
    private Double distanceToDropKm;
    private Long riderId;
    private Long pickupRiderId;
    private Long deliveryRiderId;
    private Long hubId;
    private String location;
    private String notes;
}

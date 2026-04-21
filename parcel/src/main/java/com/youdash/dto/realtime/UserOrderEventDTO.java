package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class UserOrderEventDTO {
    private Long orderId;
    private String event; // searching_rider | rider_found | payment_required | confirmed | cancelled
    /** e.g. status_updated | otp_verified | reach_destination */
    private String eventType;
    private String status;
    private Long paymentDueAtEpochMs;
    private Long riderId;
    /** When true, app can show rate-rider UI for this order. */
    private Boolean canRateRider;
    /** True when rating already exists for this order. */
    private Boolean riderRatingSubmitted;
    /** Existing rider stars for this order, when submitted. */
    private Integer riderRating;
}


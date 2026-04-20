package com.youdash.dto.realtime;

import lombok.Data;

@Data
public class RiderLocationEventDTO {
    private Long orderId;
    private Long riderId;
    private Double lat;
    private Double lng;
    private Long ts;

    /** Estimated seconds until rider reaches drop location. */
    private Integer etaSeconds;

    /** Remaining driving distance to drop location in km. */
    private Double distanceToDropKm;

    /** True when rider is within 300m of drop location. */
    private Boolean nearDestination;

    /** True when rider is within 50m of drop location (OTP prompt). */
    private Boolean atDestination;
}


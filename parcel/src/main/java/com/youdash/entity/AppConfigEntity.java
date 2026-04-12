package com.youdash.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_price_config")
@Data
public class AppConfigEntity {

    @Id
    private Long id;

    /** GST as percent, e.g. 18 for 18% */
    @Column(name = "gst_percent")
    private Double gstPercent;

    @Column(name = "platform_fee")
    private Double platformFee;

    @Column(name = "pickup_rate_per_km")
    private Double pickupRatePerKm;

    @Column(name = "drop_rate_per_km")
    private Double dropRatePerKm;

    @Column(name = "per_kg_rate")
    private Double perKgRate;

    /** Used when no hub-route row exists for a hub pair */
    @Column(name = "default_route_rate_per_km")
    private Double defaultRouteRatePerKm;
}

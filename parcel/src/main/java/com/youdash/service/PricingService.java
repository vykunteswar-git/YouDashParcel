package com.youdash.service;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.model.OutstationDeliveryType;
import lombok.Builder;
import lombok.Data;

/**
 * Central pricing calculations (no HTTP).
 */
public interface PricingService {

    double incityVehicleTotal(double distanceKm, double weightKg, VehicleEntity vehicle);

    /**
     * @param pickupDistanceKm  pickup → origin hub (0 if not used)
     * @param hubDistanceKm     origin hub → destination hub
     * @param dropDistanceKm    destination hub → drop (0 if not used)
     */
    OutstationBreakdown outstationBreakdown(
            double pickupDistanceKm,
            double hubDistanceKm,
            double dropDistanceKm,
            double routeRatePerKm,
            double weightKg,
            OutstationDeliveryType deliveryType,
            AppConfigEntity config);

    @Data
    @Builder
    class OutstationBreakdown {
        private double pickupDistanceKm;
        private double hubDistanceKm;
        private double dropDistanceKm;
        private double pickupCost;
        private double hubCost;
        private double dropCost;
        private double weightCost;
        private double subtotal;
        private double gstAmount;
        private double platformFee;
        private double total;
    }
}

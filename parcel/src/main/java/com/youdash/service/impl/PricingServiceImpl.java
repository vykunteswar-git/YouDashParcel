package com.youdash.service.impl;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.model.OutstationDeliveryType;
import com.youdash.service.PricingService;
import org.springframework.stereotype.Service;

@Service
public class PricingServiceImpl implements PricingService {

    @Override
    public double incityVehicleTotal(double distanceKm, double weightKg, VehicleEntity vehicle) {
        double minKm = vehicle.getMinimumKm() != null ? vehicle.getMinimumKm() : 0.0;
        double billableKm = Math.max(distanceKm, minKm);
        double base = vehicle.getBaseFare() != null ? vehicle.getBaseFare() : 0.0;
        double rate = vehicle.getPricePerKm() != null ? vehicle.getPricePerKm() : 0.0;
        return base + billableKm * rate;
    }

    @Override
    public OutstationBreakdown outstationBreakdown(
            double pickupDistanceKm,
            double hubDistanceKm,
            double dropDistanceKm,
            double routeRatePerKm,
            double weightKg,
            OutstationDeliveryType deliveryType,
            AppConfigEntity config) {

        double pickupRate = nz(config.getPickupRatePerKm());
        double dropRate = nz(config.getDropRatePerKm());
        double perKg = nz(config.getPerKgRate());
        double gstPct = nz(config.getGstPercent());
        double platform = nz(config.getPlatformFee());

        double pickupDist = pickupDistanceKm;
        double dropDist = dropDistanceKm;

        switch (deliveryType) {
            case DOOR_TO_DOOR -> { /* use legs as passed */ }
            case DOOR_TO_HUB -> {
                dropDist = 0.0;
            }
            case HUB_TO_DOOR -> {
                pickupDist = 0.0;
            }
            default -> throw new IllegalArgumentException("Unknown delivery type");
        }

        double pickupCost = pickupDist * pickupRate;
        double hubCost = hubDistanceKm * routeRatePerKm;
        double dropCost = dropDist * dropRate;
        double weightCost = weightKg * perKg;

        double subtotal = pickupCost + hubCost + dropCost + weightCost;
        double gstAmount = subtotal * (gstPct / 100.0);
        double total = subtotal + gstAmount + platform;

        return OutstationBreakdown.builder()
                .pickupDistanceKm(round4(pickupDist))
                .hubDistanceKm(round4(hubDistanceKm))
                .dropDistanceKm(round4(dropDist))
                .pickupCost(round2(pickupCost))
                .hubCost(round2(hubCost))
                .dropCost(round2(dropCost))
                .weightCost(round2(weightCost))
                .subtotal(round2(subtotal))
                .gstAmount(round2(gstAmount))
                .platformFee(round2(platform))
                .total(round2(total))
                .build();
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}

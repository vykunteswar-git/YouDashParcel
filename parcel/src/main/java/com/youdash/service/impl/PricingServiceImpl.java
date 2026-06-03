package com.youdash.service.impl;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.WeightCostSlabEntity;
import com.youdash.model.OutstationDeliveryType;
import com.youdash.model.OutstationLegType;
import com.youdash.repository.WeightCostSlabRepository;
import com.youdash.service.OutstationLegRateResolver;
import com.youdash.service.OutstationLegRateResolver.TierResult;
import com.youdash.service.PricingService;
import com.youdash.util.AppConfigPricing;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PricingServiceImpl implements PricingService {

    @Autowired
    private OutstationLegRateResolver legRateResolver;

    @Autowired
    private WeightCostSlabRepository weightCostSlabRepository;

    @Override
    public double incityVehicleTotal(double distanceKm, double weightKg, VehicleEntity vehicle) {
        double minKm = vehicle.getMinimumKm() != null ? vehicle.getMinimumKm() : 0.0;
        double base = vehicle.getBaseFare() != null ? vehicle.getBaseFare() : 0.0;
        double rate = vehicle.getPricePerKm() != null ? vehicle.getPricePerKm() : 0.0;
        if (minKm > 0.0 && distanceKm < minKm) {
            return base;
        }
        return base + distanceKm * rate;
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

        TierResult pickupTier = legRateResolver.resolveTier(OutstationLegType.PICKUP, weightKg, config);
        TierResult dropTier   = legRateResolver.resolveTier(OutstationLegType.DROP,   weightKg, config);

        double perKgRate = nz(config.getPerKgRate());
        double gstPct    = nz(config.getGstPercent());
        double platform  = AppConfigPricing.outstationPlatformFee(config);

        double pickupDist = pickupDistanceKm;
        double dropDist   = dropDistanceKm;

        switch (deliveryType) {
            case DOOR_TO_DOOR -> { /* use legs as passed */ }
            case DOOR_TO_HUB  -> dropDist   = 0.0;
            case HUB_TO_DOOR  -> pickupDist = 0.0;
            default -> throw new IllegalArgumentException("Unknown delivery type");
        }

        double pickupCost = legCost(pickupDist, pickupTier);
        double hubCost    = round2(hubDistanceKm * routeRatePerKm);
        double dropCost   = legCost(dropDist, dropTier);
        double weightCost = resolveWeightCost(weightKg, perKgRate);

        double subtotal  = round2(pickupCost + hubCost + dropCost + weightCost);
        double gstAmount = round2(subtotal * (gstPct / 100.0));
        double total     = round2(subtotal + gstAmount + platform);

        return OutstationBreakdown.builder()
                .pickupDistanceKm(round4(pickupDist))
                .hubDistanceKm(round4(hubDistanceKm))
                .dropDistanceKm(round4(dropDist))
                .pickupRatePerKm(round2(pickupTier.getRatePerKm()))
                .dropRatePerKm(round2(dropTier.getRatePerKm()))
                .pickupBaseFare(round2(pickupTier.getBaseFare()))
                .dropBaseFare(round2(dropTier.getBaseFare()))
                .pickupVehicleName(pickupTier.getVehicleName())
                .dropVehicleName(dropTier.getVehicleName())
                .pickupCost(round2(pickupCost))
                .hubCost(round2(hubCost))
                .dropCost(round2(dropCost))
                .weightCost(round2(weightCost))
                .subtotal(subtotal)
                .gstAmount(gstAmount)
                .platformFee(round2(platform))
                .total(total)
                .build();
    }

    /**
     * Flat charge from weight slab. Falls back to perKgRate × weightKg when no slab matches.
     */
    private double resolveWeightCost(double weightKg, double perKgRate) {
        if (weightKg <= 0.0) return 0.0;
        List<WeightCostSlabEntity> slabs = weightCostSlabRepository
                .findByIsActiveTrueOrderBySortOrderAscMinWeightKgAsc();
        for (WeightCostSlabEntity slab : slabs) {
            double min = nz(slab.getMinWeightKg());
            double max = nz(slab.getMaxWeightKg());
            if (weightKg >= min && weightKg < max) {
                return round2(nz(slab.getFlatCost()));
            }
        }
        return round2(weightKg * perKgRate);
    }

    /**
     * If distance < minimumKm → charge baseFare only.
     * Otherwise → baseFare + distance × ratePerKm.
     */
    private static double legCost(double distanceKm, TierResult tier) {
        if (distanceKm <= 0.0) return 0.0;
        if (distanceKm < tier.getMinimumKm()) {
            return round2(tier.getBaseFare());
        }
        return round2(tier.getBaseFare() + distanceKm * tier.getRatePerKm());
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

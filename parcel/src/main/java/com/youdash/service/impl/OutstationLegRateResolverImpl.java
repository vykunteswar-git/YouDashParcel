package com.youdash.service.impl;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.OutstationLegRateTierEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.model.OutstationLegType;
import com.youdash.repository.OutstationLegRateTierRepository;
import com.youdash.repository.VehicleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import com.youdash.service.OutstationLegRateResolver;

@Service
public class OutstationLegRateResolverImpl implements OutstationLegRateResolver {

    @Autowired
    private OutstationLegRateTierRepository tierRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Override
    public TierResult resolveTier(OutstationLegType legType, double weightKg, AppConfigEntity config) {
        List<OutstationLegRateTierEntity> tiers =
                tierRepository.findByLegTypeAndIsActiveTrueOrderBySortOrderAscMinWeightKgAsc(legType);
        for (OutstationLegRateTierEntity tier : tiers) {
            double min = nz(tier.getMinWeightKg());
            double max = nz(tier.getMaxWeightKg());
            if (weightKg >= min && weightKg < max) {
                String vehicleName = resolveVehicleName(tier.getVehicleId());
                return TierResult.builder()
                        .baseFare(nz(tier.getBaseFare()))
                        .minimumKm(nz(tier.getMinimumKm()))
                        .ratePerKm(nz(tier.getRatePerKm()))
                        .vehicleName(vehicleName)
                        .build();
            }
        }
        return legacyFallback(legType, config);
    }

    private String resolveVehicleName(Long vehicleId) {
        if (vehicleId == null) return null;
        return vehicleRepository.findById(vehicleId)
                .map(VehicleEntity::getName)
                .orElse(null);
    }

    private static TierResult legacyFallback(OutstationLegType legType, AppConfigEntity config) {
        double rate = 0.0;
        if (config != null) {
            rate = legType == OutstationLegType.PICKUP
                    ? nz(config.getPickupRatePerKm())
                    : nz(config.getDropRatePerKm());
        }
        return TierResult.builder().baseFare(0.0).minimumKm(0.0).ratePerKm(rate).build();
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }
}

package com.youdash.service.impl;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.OutstationLegRateTierEntity;
import com.youdash.model.OutstationLegType;
import com.youdash.repository.OutstationLegRateTierRepository;
import com.youdash.service.OutstationLegRateResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OutstationLegRateResolverImpl implements OutstationLegRateResolver {

    @Autowired
    private OutstationLegRateTierRepository tierRepository;

    @Override
    public double resolveRatePerKm(OutstationLegType legType, double weightKg, AppConfigEntity config) {
        if (weightKg <= 0) {
            return legacyFlatRate(legType, config);
        }
        List<OutstationLegRateTierEntity> tiers =
                tierRepository.findByLegTypeAndIsActiveTrueOrderBySortOrderAscMinWeightKgAsc(legType);
        for (OutstationLegRateTierEntity tier : tiers) {
            double min = nz(tier.getMinWeightKg());
            double max = nz(tier.getMaxWeightKg());
            if (weightKg >= min && weightKg < max) {
                return nz(tier.getRatePerKm());
            }
        }
        return legacyFlatRate(legType, config);
    }

    private static double legacyFlatRate(OutstationLegType legType, AppConfigEntity config) {
        if (config == null) {
            return 0.0;
        }
        if (legType == OutstationLegType.PICKUP) {
            return nz(config.getPickupRatePerKm());
        }
        return nz(config.getDropRatePerKm());
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }
}

package com.youdash.service;

import com.youdash.entity.AppConfigEntity;
import com.youdash.model.OutstationLegType;
import lombok.Builder;
import lombok.Data;

public interface OutstationLegRateResolver {

    /**
     * Resolves the full pricing tier for a leg and parcel weight.
     * Uses active weight tiers; falls back to legacy flat rates when no tier matches.
     */
    TierResult resolveTier(OutstationLegType legType, double weightKg, AppConfigEntity config);

    @Data
    @Builder
    class TierResult {
        private double baseFare;
        private double minimumKm;
        private double ratePerKm;
        private String vehicleName;
    }
}

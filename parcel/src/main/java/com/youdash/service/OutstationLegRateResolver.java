package com.youdash.service;

import com.youdash.entity.AppConfigEntity;
import com.youdash.model.OutstationLegType;

public interface OutstationLegRateResolver {

    /**
     * ₹/km for the given leg and parcel weight. Uses active weight tiers; falls back to
     * legacy flat {@code pickup_rate_per_km} / {@code drop_rate_per_km} when no tier matches.
     */
    double resolveRatePerKm(OutstationLegType legType, double weightKg, AppConfigEntity config);
}

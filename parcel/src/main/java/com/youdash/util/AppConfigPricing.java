package com.youdash.util;

import com.youdash.entity.AppConfigEntity;

/**
 * Resolves platform fees with backward compatibility for legacy {@code platform_fee}.
 */
public final class AppConfigPricing {

    private AppConfigPricing() {
    }

    public static double incityPlatformFee(AppConfigEntity config) {
        if (config == null) {
            return 0.0;
        }
        if (config.getIncityPlatformFee() != null) {
            return config.getIncityPlatformFee();
        }
        if (config.getPlatformFee() != null) {
            return config.getPlatformFee();
        }
        return 0.0;
    }

    public static double outstationPlatformFee(AppConfigEntity config) {
        if (config == null) {
            return 0.0;
        }
        if (config.getOutstationPlatformFee() != null) {
            return config.getOutstationPlatformFee();
        }
        if (config.getPlatformFee() != null) {
            return config.getPlatformFee();
        }
        return 0.0;
    }
}

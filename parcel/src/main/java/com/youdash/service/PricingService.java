package com.youdash.service;

import com.youdash.pricing.PricingBreakdown;

import java.math.BigDecimal;

public interface PricingService {
    PricingBreakdown calculate(BigDecimal distanceKm,
                               BigDecimal pricePerKm,
                               BigDecimal deliveryTypeFee,
                               BigDecimal platformFee,
                               BigDecimal cgstPercent,
                               BigDecimal sgstPercent);
}


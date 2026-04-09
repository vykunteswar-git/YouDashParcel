package com.youdash.service.impl;

import com.youdash.pricing.PricingBreakdown;
import com.youdash.service.PricingService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class PricingServiceImpl implements PricingService {

    private static final int MONEY_SCALE = 2;

    @Override
    public PricingBreakdown calculate(BigDecimal distanceKm,
                                      BigDecimal pricePerKm,
                                      BigDecimal deliveryTypeFee,
                                      BigDecimal platformFee,
                                      BigDecimal cgstPercent,
                                      BigDecimal sgstPercent) {

        BigDecimal safeDistance = nz(distanceKm);
        BigDecimal safePricePerKm = nz(pricePerKm);
        BigDecimal safeDeliveryFee = nz(deliveryTypeFee);
        BigDecimal safePlatformFee = nz(platformFee);
        BigDecimal safeCgstPct = nz(cgstPercent);
        BigDecimal safeSgstPct = nz(sgstPercent);

        BigDecimal base = safeDistance.multiply(safePricePerKm);
        base = money(base);

        BigDecimal gstBase = base.add(safeDeliveryFee).add(safePlatformFee);
        gstBase = money(gstBase);

        BigDecimal cgst = gstBase.multiply(safeCgstPct).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal sgst = gstBase.multiply(safeSgstPct).divide(BigDecimal.valueOf(100), MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal total = gstBase.add(cgst).add(sgst);
        total = money(total);

        PricingBreakdown breakdown = new PricingBreakdown();
        breakdown.setBase(base);
        breakdown.setDeliveryTypeFee(money(safeDeliveryFee));
        breakdown.setPlatformFee(money(safePlatformFee));
        breakdown.setGstBase(gstBase);
        breakdown.setCgst(cgst);
        breakdown.setSgst(sgst);
        breakdown.setTotal(total);
        return breakdown;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}


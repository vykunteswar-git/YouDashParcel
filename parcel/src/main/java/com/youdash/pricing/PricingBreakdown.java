package com.youdash.pricing;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingBreakdown {
    private BigDecimal base;
    private BigDecimal deliveryTypeFee;
    private BigDecimal platformFee;
    private BigDecimal gstBase;
    private BigDecimal cgst;
    private BigDecimal sgst;
    private BigDecimal total;
}


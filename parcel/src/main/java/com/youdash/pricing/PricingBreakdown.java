package com.youdash.pricing;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PricingBreakdown {
    private BigDecimal base;
    private BigDecimal deliveryTypeFee;
    private BigDecimal platformFee;
    private BigDecimal gstBase;
    private BigDecimal gst;
    private BigDecimal total;
}


package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTypePricingDTO {

    private String currency;
    private QuotePricingBreakdownDTO breakdown;
    /** Legs + weight before GST and platform fee (matches {@link com.youdash.service.PricingService.OutstationBreakdown#getSubtotal()}) */
    private Double subtotal;
    private Double platformFee;
    private QuoteGstPricingDTO gst;
    private Double total;
}

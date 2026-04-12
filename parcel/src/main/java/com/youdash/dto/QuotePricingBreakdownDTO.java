package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuotePricingBreakdownDTO {

    private QuoteLegPricingDTO pickup;
    private QuoteLegPricingDTO hubToHub;
    /** Destination hub → drop (last mile) */
    private QuoteLegPricingDTO lastMile;
    private QuoteWeightPricingDTO weight;
}

package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteWeightPricingDTO {

    private Double kg;
    private Double ratePerKg;
    private Double cost;
}

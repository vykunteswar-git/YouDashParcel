package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLegPricingDTO {

    private Double distanceKm;
    private Double ratePerKm;
    private Double cost;
}

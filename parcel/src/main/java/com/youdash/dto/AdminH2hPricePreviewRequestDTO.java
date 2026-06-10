package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

@Data
public class AdminH2hPricePreviewRequestDTO {

    private Long originHubId;
    private Long destinationHubId;
    private Double weight;
    /** {@code KG}, {@code G}, or UI labels like {@code Grams (g)} / {@code Kilograms (kg)}. */
    @JsonAlias({"weight_unit", "unit", "weightUnitLabel"})
    private String weightUnit;
}

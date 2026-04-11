package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
@Schema(description = "Per-zone incity distance rates")
public class ZonePricingRequestDTO {

    @NotNull
    @Schema(example = "1")
    private Long zoneId;

    @NotNull
    @PositiveOrZero
    @Schema(example = "8")
    private Double pickupRatePerKm;

    @NotNull
    @PositiveOrZero
    @Schema(example = "8")
    private Double deliveryRatePerKm;

    @NotNull
    @PositiveOrZero
    @Schema(example = "30")
    private Double baseFare;
}

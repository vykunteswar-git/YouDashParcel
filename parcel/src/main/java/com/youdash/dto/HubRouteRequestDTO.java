package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
@Schema(description = "Inter-hub outstation leg pricing")
public class HubRouteRequestDTO {

    @NotNull
    @Schema(example = "1")
    private Long sourceHubId;

    @NotNull
    @Schema(example = "2")
    private Long destinationHubId;

    @PositiveOrZero
    @Schema(example = "1.5")
    private Double pricePerKm;

    @PositiveOrZero
    @Schema(example = "900")
    private Double fixedPrice;
}

package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "Global delivery pricing knobs (incity extension, floors)")
public class GlobalDeliveryConfigDTO {

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @NotNull
    @PositiveOrZero
    @Schema(example = "5")
    private Double incityExtensionKm;

    @NotNull
    @PositiveOrZero
    @Schema(example = "10")
    private Double incityExtraRatePerKm;

    @NotNull
    @PositiveOrZero
    @Schema(example = "30")
    private Double baseFare;

    @NotNull
    @PositiveOrZero
    @Schema(example = "80")
    private Double minimumCharge;

    @NotNull
    @PositiveOrZero
    @DecimalMax(value = "100.0", message = "must be <= 100")
    @Schema(example = "5")
    private Double gstPercent;

    @NotNull
    @PositiveOrZero
    @Schema(example = "20")
    private Double platformFee;

    @PositiveOrZero
    @Schema(description = "Outstation: ₹/km pickup→nearest hub. Omit to use incityExtraRatePerKm.", example = "8")
    private Double firstMileRatePerKm;

    @PositiveOrZero
    @Schema(description = "Outstation: ₹/km destination hub→drop. Omit to use incityExtraRatePerKm.", example = "8")
    private Double lastMileRatePerKm;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime createdAt;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private LocalDateTime updatedAt;
}

package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Create or replace hub (outstation routing)")
public class HubRequestDTO {

    @NotBlank
    @Schema(example = "Mumbai")
    private String city;

    @NotBlank
    @Schema(example = "Mumbai Central Hub")
    private String name;

    @NotNull
    @DecimalMin(value = "-90.0", inclusive = true)
    @DecimalMax(value = "90.0", inclusive = true)
    @Schema(example = "19.0760")
    private Double lat;

    @NotNull
    @DecimalMin(value = "-180.0", inclusive = true)
    @DecimalMax(value = "180.0", inclusive = true)
    @Schema(example = "72.8777")
    private Double lng;

    @Schema(description = "Defaults to true when omitted on create")
    private Boolean isActive;
}

package com.youdash.dto;

import com.youdash.model.DeliveryOptionCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Create or update a delivery option row (UI keys)")
public class DeliveryOptionRequestDTO {

    @NotNull
    private DeliveryOptionCategory category;

    @NotBlank
    @Schema(example = "STANDARD")
    private String code;

    @Schema(description = "Lower sorts first", example = "0")
    private Integer sortOrder;

    @Schema(description = "Defaults to true on create")
    private Boolean isActive;
}

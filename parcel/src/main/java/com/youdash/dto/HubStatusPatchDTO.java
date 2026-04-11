package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Toggle hub active flag")
public class HubStatusPatchDTO {

    @NotNull
    @Schema(example = "true")
    private Boolean isActive;
}

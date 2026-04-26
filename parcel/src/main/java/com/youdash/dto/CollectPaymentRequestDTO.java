package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Rider marks COD as collected before completing delivery")
public class CollectPaymentRequestDTO {

    @Schema(description = "Numeric internal order id", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long orderId;

    @Schema(description = "CASH or QR", example = "CASH", requiredMode = Schema.RequiredMode.REQUIRED)
    private String mode;
}

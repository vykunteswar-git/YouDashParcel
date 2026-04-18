package com.youdash.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Rider completes delivery. ONLINE: orderId only. COD: orderId + cod fields.")
public class OrderCompleteRequestDTO {
    @Schema(description = "Numeric internal id or YP- display reference", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderId;

    @Schema(description = "Amount collected (COD only; must be > 0)")
    private Double codCollectedAmount;

    @Schema(description = "CASH or QR (required for COD)", example = "CASH")
    private String codCollectionMode;
}

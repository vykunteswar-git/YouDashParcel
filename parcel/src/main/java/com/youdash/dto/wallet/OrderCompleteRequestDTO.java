package com.youdash.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Rider completes delivery. ONLINE (pre-paid): send only orderId — amount comes from the order/gateway. "
        + "COD: send orderId + codCollectionMode. COD amount is auto-picked from order total.")
public class OrderCompleteRequestDTO {
    @Schema(description = "Numeric internal id or YP- display reference", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderId;

    @Schema(description = "Deprecated input. Ignored for both ONLINE and COD; backend uses order total/paid amount.")
    private Double codCollectedAmount;

    @Schema(description = "CASH or QR (COD only; required for COD; ignored for ONLINE)", example = "CASH")
    private String codCollectionMode;
}

package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "RazorpayVerifyPaymentRequest", description = "Verify Razorpay payment after checkout success")
public class RazorpayVerifyPaymentRequestDTO {

    @Schema(
            description = "Internal DB primary key (id) from POST /orders, OR display order_id like YP-...",
            example = "YP-1775817839429",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String orderId;

    @Schema(description = "Razorpay order id", example = "order_Pmxxxxxxxxxxxx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String razorpayOrderId;

    @Schema(description = "Razorpay payment id", example = "pay_Pmxxxxxxxxxxxx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String razorpayPaymentId;

    @Schema(description = "Razorpay signature", example = "d4c3b2a1...", requiredMode = Schema.RequiredMode.REQUIRED)
    private String razorpaySignature;
}


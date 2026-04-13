package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "RazorpayVerifyRequest", description = "Verify Razorpay payment after checkout success")
public class RazorpayVerifyRequestDTO {

    @Schema(description = "Internal id or display order id (YP-…)", example = "123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String orderId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String razorpayOrderId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String razorpayPaymentId;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String razorpaySignature;
}

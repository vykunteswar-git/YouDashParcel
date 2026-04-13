package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(name = "RazorpayCreateOrderRequest", description = "Create a Razorpay order for an existing internal order")
public class RazorpayCreateOrderRequestDTO {

    @Schema(
            description = "Internal DB primary key (id) from POST /orders, OR display reference like YP-…",
            example = "YP-11775817839429",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    private String orderId;
}

package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Fields needed to open Razorpay Checkout (amount is in paise)")
public class RazorpayOrderCreatedDTO {

    @Schema(description = "Razorpay order id", example = "order_Pmxxxxxxxxxxxx")
    private String razorpayOrderId;

    @Schema(description = "Amount in paise (INR smallest unit)", example = "12300")
    private Long amount;

    @Schema(description = "Currency code", example = "INR")
    private String currency;

    @Schema(description = "Razorpay Key Id for client SDK", example = "rzp_test_xxxx")
    private String keyId;
}

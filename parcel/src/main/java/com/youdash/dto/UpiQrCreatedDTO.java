package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Razorpay UPI QR code created for COD collection")
public class UpiQrCreatedDTO {

    @Schema(description = "Razorpay QR code id (qr_xxx)")
    private String qrId;

    @Schema(description = "URL to the QR code image (PNG)")
    private String imageUrl;
}

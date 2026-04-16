package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class OrderCompleteRequestDTO {
    /** Numeric internal id or YP- display id */
    private String orderId;
    private Double codCollectedAmount;
    /** CASH or QR (required for COD) */
    private String codCollectionMode;
}

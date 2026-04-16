package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class AdminCodSettleRequestDTO {
    /** Numeric internal id or YP- display id */
    private String orderId;
    /** Must match rider's collected COD for that order */
    private Double amount;
}

package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class AdminCodOpenLineDTO {
    private Long orderId;
    private String displayOrderId;
    private Double orderAmount;
    private Double commissionAmount;
    private Double riderEarningAmount;
    private Double commissionPercentApplied;
    private String codCollectionMode;
    private String deliveredAt;
}

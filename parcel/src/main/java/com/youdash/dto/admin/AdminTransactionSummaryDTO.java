package com.youdash.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTransactionSummaryDTO {
    private String from;
    private String to;
    private Double totalVolume;
    private Double activePayoutAmount;
    private Double paymentGatewayVolume;
    private Long totalTransactions;
}

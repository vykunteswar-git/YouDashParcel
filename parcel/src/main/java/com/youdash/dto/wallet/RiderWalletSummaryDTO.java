package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderWalletSummaryDTO {
    private Double currentBalance;
    private Double totalEarnings;
    private Double totalWithdrawn;
    private Double codPendingAmount;
    private Double withdrawalPendingAmount;
    /** currentBalance - codPendingAmount - withdrawalPendingAmount */
    private Double netAvailable;
    private Long totalOrdersDelivered;
}

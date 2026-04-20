package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderWalletSummaryDTO {
    private Double currentBalance;
    /** Spendable balance after excluding COD pending and pending withdrawals. */
    private Double availableBalance;
    private Double todayEarnings;
    private Double thisWeekEarnings;
    private Double thisMonthEarnings;
    private Double totalEarnings;
    private Double totalWithdrawn;
    private Double codPendingAmount;
    private Double withdrawalPendingAmount;
    /** currentBalance - codPendingAmount - withdrawalPendingAmount */
    private Double netAvailable;
    private Long totalOrdersDelivered;
}

package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderWalletSummaryDTO {
    private Double currentBalance;
    /** Spendable balance after excluding pending withdrawals (same basis as withdrawals). */
    private Double availableBalance;
    private Double todayEarnings;
    private Double thisWeekEarnings;
    private Double thisMonthEarnings;
    private Double totalEarnings;
    private Double totalWithdrawn;
    /** COD commission (cash) to deposit at hub — not full order value. */
    private Double codPendingAmount;
    private Double withdrawalPendingAmount;
    /** Spendable for withdrawal: currentBalance - withdrawalPendingAmount (COD liability is in codPendingAmount only). */
    private Double netAvailable;
    private Long totalOrdersDelivered;
    private Double codHandoverLimit;
    /** OK | WARNING | BLOCKED */
    private String codHandoverStatus;
    private Boolean dispatchBlocked;
}

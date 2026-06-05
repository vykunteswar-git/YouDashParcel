package com.youdash.dto.admin;

import lombok.Data;
import java.util.List;

@Data
public class AdminEarningsDTO {
    private String range;

    // ── Summary cards ────────────────────────────────────────────────────────
    private int orderCount;
    /** Sum of order.totalAmount for all DELIVERED orders in range. */
    private Double totalRevenue;
    /** Sum of commissionAmount across all financial rows in range. */
    private Double totalCommission;
    /** Sum of order.gstAmount for all DELIVERED orders in range. */
    private Double totalGst;
    /** Sum of order.platformFee for all DELIVERED orders in range. */
    private Double totalPlatformFee;
    /** totalCommission + totalGst + totalPlatformFee. */
    private Double totalPlatformNet;
    /** Sum of riderEarningAmount across all financial rows in range. */
    private Double totalRiderPayouts;

    // ── Per-order rows ───────────────────────────────────────────────────────
    private List<AdminEarningsOrderRowDTO> orders;
}

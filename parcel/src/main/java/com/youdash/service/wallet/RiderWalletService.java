package com.youdash.service.wallet;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.wallet.*;
import com.youdash.entity.OrderEntity;
import com.youdash.model.wallet.CodCollectionMode;

public interface RiderWalletService {

    ApiResponse<RiderWalletSummaryDTO> getWalletSummary(Long riderId);

    ApiResponse<java.util.List<RiderWalletTransactionDTO>> listTransactions(Long riderId, int page, int size);

    ApiResponse<java.util.List<RiderWithdrawalDTO>> listWithdrawals(Long riderId, int page, int size);

    ApiResponse<RiderWithdrawalDTO> requestWithdrawal(Long riderId, RiderWithdrawalRequestDTO dto);

    ApiResponse<RiderWithdrawalDTO> adminApproveWithdrawal(Long adminUserId, AdminWithdrawalApproveDTO dto);

    ApiResponse<java.util.List<RiderWithdrawalDTO>> adminListWithdrawals(String status, int page, int size);

    ApiResponse<RiderCommissionConfigDTO> upsertCommissionConfig(Long adminUserId, RiderCommissionConfigDTO dto);

    ApiResponse<RiderCommissionConfigDTO> getCommissionConfig();

    /**
     * Idempotent settlement when an order becomes DELIVERED.
     */
    void settleOrderDelivered(OrderEntity order, CodCollectionMode codMode, Double codCollectedAmount, Long actorUserId, String actorType);

    /**
     * True when all expected per-rider financial rows exist for this order (one for INCITY / single rider,
     * two for OUTSTATION with different pickup and delivery riders).
     */
    boolean isOrderWalletSettlementComplete(OrderEntity order);

    void ensureDefaultCommissionConfig();

    ApiResponse<String> adminSettleCod(Long adminUserId, AdminCodSettleRequestDTO dto);

    /**
     * Rider earning from current commission config (percentage of order amount),
     * same formula as delivery settlement before wallet rows exist.
     */
    double estimateRiderEarningForOrder(OrderEntity order);

    /**
     * Sum of settled rider earnings for all riders on this order; if none settled yet, full-trip estimate
     * (same as a single rider would see before split).
     */
    double resolveRiderEarningForOrder(OrderEntity order);

    /**
     * This rider's settled earning for the order, or an estimate from first/last-mile share for OUTSTATION split riders.
     */
    double resolveRiderEarningForOrder(OrderEntity order, Long riderId);
}

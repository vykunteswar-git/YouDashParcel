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

    ApiResponse<RiderCommissionConfigDTO> upsertCommissionConfig(Long adminUserId, RiderCommissionConfigDTO dto);

    ApiResponse<RiderCommissionConfigDTO> getCommissionConfig();

    /**
     * Idempotent settlement when an order becomes DELIVERED.
     */
    void settleOrderDelivered(OrderEntity order, CodCollectionMode codMode, Double codCollectedAmount, Long actorUserId, String actorType);

    /** True if a rider financial snapshot row exists for this order (settlement completed or in progress). */
    boolean hasOrderRiderFinancial(Long orderId);

    void ensureDefaultCommissionConfig();

    ApiResponse<String> adminSettleCod(Long adminUserId, AdminCodSettleRequestDTO dto);

    /**
     * Rider earning from current commission config (percentage of order amount),
     * same formula as delivery settlement before wallet rows exist.
     */
    double estimateRiderEarningForOrder(OrderEntity order);

    /** Settled {@link com.youdash.entity.wallet.OrderRiderFinancialEntity} amount when present, otherwise {@link #estimateRiderEarningForOrder}. */
    double resolveRiderEarningForOrder(OrderEntity order);
}

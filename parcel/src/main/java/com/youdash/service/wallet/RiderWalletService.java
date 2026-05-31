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
     * Re-runs delivery settlement for DELIVERED orders when the delivery rider wallet credit is still missing.
     * Safe to call multiple times (e.g. order fetch, complete retry, admin status).
     */
    void ensureDeliverySettlementIfNeeded(OrderEntity order);

    /**
     * Scans recent DELIVERED outstation orders for this rider and credits any missing delivery-leg wallet rows.
     * Called when the rider opens Wallet or Orders so stuck credits self-heal without a status change.
     */
    void repairPendingDeliveryWalletCredits(Long riderId);

    /** True when this rider already has a completed ORDER wallet credit for the given order. */
    boolean isRiderWalletCreditedForOrder(Long riderId, Long orderId);

    /**
     * Settle the pickup leg for an OUTSTATION split order when the pickup rider
     * marks AT_ORIGIN_HUB. Idempotent — safe to call multiple times.
     */
    void settlePickupLegAtOriginHub(OrderEntity order, Long pickupRiderId);

    /**
     * True when all expected per-rider financial rows exist for this order (one for INCITY / single rider,
     * two for OUTSTATION with different pickup and delivery riders).
     */
    boolean isOrderWalletSettlementComplete(OrderEntity order);

    void ensureDefaultCommissionConfig();

    ApiResponse<String> adminSettleCod(Long adminUserId, AdminCodSettleRequestDTO dto);

    /** True when COD commission pending is at or above the rider's handover limit. */
    boolean isRiderDispatchBlocked(Long riderId);

    ApiResponse<java.util.List<com.youdash.dto.wallet.AdminCodRiderSummaryDTO>> adminListCodRiders(
            String statusFilter, String search);

    ApiResponse<com.youdash.dto.wallet.AdminCodRiderDetailDTO> adminGetCodRiderDetail(Long riderId);

    ApiResponse<String> adminConfirmCodDeposit(Long adminUserId, AdminCodDepositRequestDTO dto);

    ApiResponse<com.youdash.dto.RiderResponseDTO> adminUpdateCodHandoverLimit(
            Long riderId, com.youdash.dto.wallet.AdminCodHandoverLimitRequestDTO dto);

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

package com.youdash.service.wallet.impl;

import java.time.Instant;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.temporal.TemporalAdjusters;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.dto.wallet.AdminCodDepositHistoryDTO;
import com.youdash.dto.wallet.AdminCodDepositRequestDTO;
import com.youdash.dto.wallet.AdminCodHandoverLimitRequestDTO;
import com.youdash.dto.wallet.AdminCodOpenLineDTO;
import com.youdash.dto.wallet.AdminCodRiderDetailDTO;
import com.youdash.dto.wallet.AdminCodRiderSummaryDTO;
import com.youdash.dto.wallet.AdminWithdrawalApproveDTO;
import com.youdash.dto.wallet.AdminCodSettleRequestDTO;
import com.youdash.dto.wallet.RiderCommissionConfigDTO;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.wallet.CodDepositEntity;
import com.youdash.dto.wallet.RiderWalletSummaryDTO;
import com.youdash.dto.wallet.RiderWalletTransactionDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;
import com.youdash.dto.wallet.RiderWithdrawalRequestDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.wallet.FinAuditLogEntity;
import com.youdash.entity.wallet.OrderRiderFinancialEntity;
import com.youdash.entity.wallet.RiderCommissionConfigEntity;
import com.youdash.entity.wallet.RiderWalletEntity;
import com.youdash.entity.wallet.RiderWalletTransactionEntity;
import com.youdash.entity.wallet.RiderWithdrawalEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;
import com.youdash.model.wallet.CodSettlementStatus;
import com.youdash.model.wallet.WalletTxnReferenceType;
import com.youdash.model.wallet.WalletTxnStatus;
import com.youdash.model.wallet.WalletTxnType;
import com.youdash.model.wallet.WithdrawalStatus;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.wallet.CodDepositRepository;
import com.youdash.repository.wallet.FinAuditLogRepository;
import com.youdash.repository.wallet.OrderRiderFinancialRepository;
import com.youdash.repository.wallet.RiderCommissionConfigRepository;
import com.youdash.repository.wallet.RiderWalletRepository;
import com.youdash.repository.wallet.RiderWalletTransactionRepository;
import com.youdash.repository.wallet.RiderWithdrawalRepository;
import com.youdash.service.NotificationDedupService;
import com.youdash.service.NotificationService;
import com.youdash.service.PeakIncentiveService;
import com.youdash.service.wallet.RiderWalletService;
import com.youdash.util.OutstationCodPolicy;
import com.youdash.util.OutstationPayableLegSplit;
import com.youdash.util.OutstationRiderLegPolicy;

/**
 * Wallet, withdrawals, per-order settlement (including split OUTSTATION legs).
 */
@Service
public class RiderWalletServiceImpl implements RiderWalletService {

    private static final Logger log = LoggerFactory.getLogger(RiderWalletServiceImpl.class);

    private static final long COMMISSION_CONFIG_ID = 1L;

    private static final double DEFAULT_COD_HANDOVER_LIMIT = 1000.0;

    private static final double COD_HANDOVER_WARNING_RATIO = 0.8;

    @Autowired
    private RiderWalletRepository riderWalletRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private CodDepositRepository codDepositRepository;

    @Autowired
    private RiderWalletTransactionRepository riderWalletTransactionRepository;

    @Autowired
    private RiderWithdrawalRepository riderWithdrawalRepository;

    @Autowired
    private RiderCommissionConfigRepository riderCommissionConfigRepository;

    @Autowired
    private OrderRiderFinancialRepository orderRiderFinancialRepository;

    @Autowired
    private FinAuditLogRepository finAuditLogRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDedupService notificationDedupService;

    @Autowired
    private PeakIncentiveService peakIncentiveService;

    @Autowired
    @Lazy
    private RiderWalletService self;

    @PostConstruct
    void initDefaults() {
        ensureDefaultCommissionConfig();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isOrderWalletSettlementComplete(OrderEntity order) {
        if (order == null || order.getId() == null) {
            return false;
        }
        int need = expectedSettlementLegCount(order);
        long finCount = orderRiderFinancialRepository.countByOrderId(order.getId());
        if (finCount < need) {
            return false;
        }
        // Split / delivery-leg outstation: delivery rider must have a wallet credit
        // txn.
        if (OutstationCodPolicy.hasSplitPickupAndDeliveryRiders(order)) {
            Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
            return deliveryId != null && hasCompletedWalletCreditForOrder(deliveryId, order.getId());
        }
        if (order.getServiceMode() == ServiceMode.OUTSTATION
                && OutstationCodPolicy.isDeliveryLegOnlyOrder(order)) {
            Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
            return deliveryId != null && hasCompletedWalletCreditForOrder(deliveryId, order.getId());
        }
        return finCount >= need;
    }

    private int expectedSettlementLegCount(OrderEntity orderEntity) {
        if (orderEntity.getServiceMode() != com.youdash.model.ServiceMode.OUTSTATION) {
            return 1;
        }
        if (OutstationCodPolicy.hasSplitPickupAndDeliveryRiders(orderEntity)) {
            return 2;
        }
        if (OutstationCodPolicy.isDoorToDoor(orderEntity)) {
            Long pickupRiderId = OutstationRiderLegPolicy.resolvePickupRiderId(orderEntity);
            Long deliveryRiderId = OutstationCodPolicy.resolveDeliveryRiderId(orderEntity);
            if (pickupRiderId != null && deliveryRiderId != null && !Objects.equals(pickupRiderId, deliveryRiderId)) {
                return 2;
            }
        }
        Long pickupRiderId = orderEntity.getPickupRiderId();
        Long deliveryRiderId = orderEntity.getDeliveryRiderId();
        if (pickupRiderId == null || deliveryRiderId == null) {
            return 1;
        }
        if (pickupRiderId.equals(deliveryRiderId)) {
            return 1;
        }
        return 2;
    }

    @Override
    public ApiResponse<RiderWalletSummaryDTO> getWalletSummary(Long riderId) {
        try {
            self.repairPendingDeliveryWalletCredits(riderId);
        } catch (Exception ex) {
            log.warn("DELIVERY_WALLET_REPAIR_SKIPPED riderId={}: {}", riderId, ex.getMessage());
        }
        return self.readWalletSummary(riderId);
    }

    @Override
    @Transactional
    public ApiResponse<RiderWalletSummaryDTO> readWalletSummary(Long riderId) {
        ApiResponse<RiderWalletSummaryDTO> response = new ApiResponse<>();
        try {
            RiderWalletEntity w = getOrCreateWallet(riderId);
            RiderWalletSummaryDTO dto = new RiderWalletSummaryDTO();
            dto.setCurrentBalance(round2(w.getCurrentBalance()));
            dto.setTotalEarnings(round2(w.getTotalEarnings()));
            dto.setTotalWithdrawn(round2(w.getTotalWithdrawn()));
            dto.setCodPendingAmount(round2(w.getCodPendingAmount()));
            dto.setWithdrawalPendingAmount(round2(w.getWithdrawalPendingAmount()));
            // currentBalance only includes rider earning credits (not full COD cash).
            // codPending tracks
            // liability separately; subtracting it here made netAvailable negative and
            // blocked withdrawals.
            double netAvailable = w.getCurrentBalance() - w.getWithdrawalPendingAmount();
            if (netAvailable < -0.0001) {
                log.warn("NEGATIVE_NET_AVAILABLE -> riderId={}, net={}", riderId, round2(netAvailable));
            }
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            Instant nowTs = now.toInstant();
            Instant startOfToday = now.toLocalDate().atStartOfDay(zone).toInstant();
            Instant startOfWeek = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .toLocalDate()
                    .atStartOfDay(zone)
                    .toInstant();
            Instant startOfMonth = now.withDayOfMonth(1)
                    .toLocalDate()
                    .atStartOfDay(zone)
                    .toInstant();

            dto.setAvailableBalance(round2(netAvailable));
            dto.setTodayEarnings(sumRiderEarningsBetween(riderId, startOfToday, nowTs));
            dto.setThisWeekEarnings(sumRiderEarningsBetween(riderId, startOfWeek, nowTs));
            dto.setThisMonthEarnings(sumRiderEarningsBetween(riderId, startOfMonth, nowTs));
            dto.setNetAvailable(round2(netAvailable));
            // Each financial row = one completed leg (pickup-at-hub or full delivery)
            dto.setTotalOrdersDelivered(orderRiderFinancialRepository.countByRiderId(riderId));
            double handoverLimit = resolveHandoverLimit(riderId);
            double commissionPending = round2(w.getCodPendingAmount());
            dto.setCodHandoverLimit(handoverLimit);
            dto.setCodHandoverStatus(resolveHandoverStatus(commissionPending, handoverLimit));
            dto.setDispatchBlocked(isDispatchBlocked(commissionPending, handoverLimit));
            response.setData(dto);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<RiderWalletTransactionDTO>> listTransactions(Long riderId, int page, int size) {
        ApiResponse<List<RiderWalletTransactionDTO>> response = new ApiResponse<>();
        try {
            var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
            List<RiderWalletTransactionDTO> list = riderWalletTransactionRepository
                    .findByRiderIdOrderByCreatedAtDesc(riderId, pageable)
                    .stream()
                    .filter(this::isRiderVisibleTransaction)
                    .map(this::toTxnDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setTotalCount(list.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<RiderWithdrawalDTO>> listWithdrawals(Long riderId, int page, int size) {
        ApiResponse<List<RiderWithdrawalDTO>> response = new ApiResponse<>();
        try {
            var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
            List<RiderWithdrawalDTO> list = riderWithdrawalRepository
                    .findByRiderIdOrderByCreatedAtDesc(riderId, pageable)
                    .stream()
                    .map(this::toWithdrawalDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setTotalCount(list.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<RiderWithdrawalDTO>> adminListWithdrawals(String status, int page, int size) {
        ApiResponse<List<RiderWithdrawalDTO>> response = new ApiResponse<>();
        try {
            var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200));
            List<RiderWithdrawalDTO> list;
            if (status == null || status.isBlank()) {
                list = riderWithdrawalRepository.findAllByOrderByCreatedAtDesc(pageable)
                        .stream()
                        .map(this::toWithdrawalDto)
                        .collect(Collectors.toList());
            } else {
                WithdrawalStatus st = WithdrawalStatus.valueOf(status.trim().toUpperCase());
                list = riderWithdrawalRepository.findByStatusOrderByCreatedAtDesc(st, pageable)
                        .stream()
                        .map(this::toWithdrawalDto)
                        .collect(Collectors.toList());
            }
            response.setData(list);
            response.setTotalCount(list.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (IllegalArgumentException e) {
            setErr(response, "Invalid status. Use PENDING, APPROVED, or REJECTED");
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<RiderWithdrawalDTO> requestWithdrawal(Long riderId, RiderWithdrawalRequestDTO dto) {
        ApiResponse<RiderWithdrawalDTO> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getAmount() == null || dto.getAmount() <= 0) {
                throw new RuntimeException("amount is required");
            }
            if (dto.getAccountHolderName() == null || dto.getAccountHolderName().isBlank()) {
                throw new RuntimeException("accountHolderName is required");
            }
            if (dto.getAccountNumber() == null || dto.getAccountNumber().isBlank()) {
                throw new RuntimeException("accountNumber is required");
            }
            if (dto.getIfsc() == null || dto.getIfsc().isBlank()) {
                throw new RuntimeException("ifsc is required");
            }

            double amount = round2(dto.getAmount());
            RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(riderId)
                    .orElseGet(() -> riderWalletRepository.save(newWallet(riderId)));

            if (wallet.getCurrentBalance() < -0.0001) {
                throw new RuntimeException("Wallet in invalid negative state");
            }

            double netAvail = wallet.getCurrentBalance() - wallet.getWithdrawalPendingAmount();
            if (amount > netAvail + 0.0001) {
                throw new RuntimeException("Insufficient wallet balance for withdrawal");
            }

            wallet.setWithdrawalPendingAmount(round2(wallet.getWithdrawalPendingAmount() + amount));
            riderWalletRepository.save(wallet);

            RiderWithdrawalEntity w = new RiderWithdrawalEntity();
            w.setRiderId(riderId);
            w.setAmount(amount);
            w.setStatus(WithdrawalStatus.PENDING);
            w.setBankAccountName(dto.getAccountHolderName().trim());
            w.setBankAccountNumber(dto.getAccountNumber().trim());
            w.setBankIfsc(dto.getIfsc().trim().toUpperCase());
            w = riderWithdrawalRepository.save(w);

            RiderWalletTransactionEntity txn = new RiderWalletTransactionEntity();
            txn.setRiderId(riderId);
            txn.setType(WalletTxnType.DEBIT);
            txn.setAmount(amount);
            txn.setReferenceType(WalletTxnReferenceType.WITHDRAWAL);
            txn.setReferenceId(w.getId());
            txn.setStatus(WalletTxnStatus.PENDING);
            txn.setNote("Withdrawal requested - funds reserved");
            riderWalletTransactionRepository.save(txn);

            audit("WITHDRAW_REQUEST", "RIDER", riderId, "WITHDRAWAL", w.getId(), Map.of(
                    "amount", amount,
                    "walletTxnId", txn.getId()));

            Map<String, String> adminWd = new HashMap<>();
            adminWd.put("withdrawalId", String.valueOf(w.getId()));
            adminWd.put("riderId", String.valueOf(riderId));
            adminWd.put("amount", String.valueOf(amount));
            adminWd.put("type", NotificationType.ADMIN_RIDER_WITHDRAWAL_REQUESTED.name());
            notificationService.sendToAdminDevices(
                    "Rider withdrawal requested",
                    "Rider " + riderId + " requested ₹" + amount + " withdrawal (id " + w.getId() + ").",
                    adminWd,
                    NotificationType.ADMIN_RIDER_WITHDRAWAL_REQUESTED);

            response.setData(toWithdrawalDto(w));
            response.setMessage("Withdrawal requested");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<RiderWithdrawalDTO> adminApproveWithdrawal(Long adminUserId, AdminWithdrawalApproveDTO dto) {
        ApiResponse<RiderWithdrawalDTO> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getWithdrawalId() == null) {
                throw new RuntimeException("withdrawalId is required");
            }
            if (dto.getApprove() == null) {
                throw new RuntimeException("approve is required (true/false)");
            }

            RiderWithdrawalEntity w = riderWithdrawalRepository.findById(dto.getWithdrawalId())
                    .orElseThrow(() -> new RuntimeException("Withdrawal not found"));
            if (w.getStatus() != WithdrawalStatus.PENDING) {
                throw new RuntimeException("Withdrawal is not pending");
            }

            RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(w.getRiderId())
                    .orElseThrow(() -> new RuntimeException("Wallet not found"));

            RiderWalletTransactionEntity holdTxn = riderWalletTransactionRepository
                    .findTopByRiderIdAndReferenceTypeAndReferenceIdAndStatusOrderByIdDesc(
                            w.getRiderId(),
                            WalletTxnReferenceType.WITHDRAWAL,
                            w.getId(),
                            WalletTxnStatus.PENDING)
                    .orElse(null);

            if (Boolean.TRUE.equals(dto.getApprove())) {
                w.setStatus(WithdrawalStatus.APPROVED);
                riderWithdrawalRepository.save(w);

                if (wallet.getCurrentBalance() < -0.0001) {
                    throw new RuntimeException("Wallet in invalid negative state");
                }

                if (wallet.getWithdrawalPendingAmount() + 0.0001 < w.getAmount()) {
                    throw new RuntimeException("Wallet withdrawal pending mismatch");
                }
                wallet.setWithdrawalPendingAmount(round2(wallet.getWithdrawalPendingAmount() - w.getAmount()));
                wallet.setCurrentBalance(round2(wallet.getCurrentBalance() - w.getAmount()));
                wallet.setTotalWithdrawn(round2(wallet.getTotalWithdrawn() + w.getAmount()));
                riderWalletRepository.save(wallet);

                if (holdTxn != null) {
                    holdTxn.setStatus(WalletTxnStatus.COMPLETED);
                    holdTxn.setNote("Withdrawal approved");
                    riderWalletTransactionRepository.save(holdTxn);
                }

                audit("WITHDRAW_APPROVED", "ADMIN", adminUserId, "WITHDRAWAL", w.getId(),
                        Map.of("riderId", w.getRiderId(), "amount", w.getAmount()));
                if (notificationDedupService.tryAcquire("rider-withdrawal-approved:" + w.getId())) {
                    Map<String, String> d = new HashMap<>();
                    d.put("withdrawalId", String.valueOf(w.getId()));
                    d.put("amount", String.valueOf(w.getAmount()));
                    d.put("type", NotificationType.RIDER_WITHDRAWAL_APPROVED.name());
                    notificationService.sendToRider(
                            w.getRiderId(),
                            "Withdrawal approved",
                            "Your withdrawal of ₹" + w.getAmount() + " was approved.",
                            d,
                            NotificationType.RIDER_WITHDRAWAL_APPROVED);
                }
            } else {
                w.setStatus(WithdrawalStatus.REJECTED);
                riderWithdrawalRepository.save(w);

                wallet.setWithdrawalPendingAmount(round2(wallet.getWithdrawalPendingAmount() - w.getAmount()));
                riderWalletRepository.save(wallet);

                if (holdTxn != null) {
                    holdTxn.setStatus(WalletTxnStatus.REVERSED);
                    holdTxn.setNote("Withdrawal rejected - reservation released (balance unchanged)");
                    riderWalletTransactionRepository.save(holdTxn);
                }

                audit("WITHDRAW_REJECTED", "ADMIN", adminUserId, "WITHDRAWAL", w.getId(),
                        Map.of("riderId", w.getRiderId(), "amount", w.getAmount()));
                if (notificationDedupService.tryAcquire("rider-withdrawal-rejected:" + w.getId())) {
                    Map<String, String> d = new HashMap<>();
                    d.put("withdrawalId", String.valueOf(w.getId()));
                    d.put("amount", String.valueOf(w.getAmount()));
                    d.put("type", NotificationType.RIDER_WITHDRAWAL_REJECTED.name());
                    notificationService.sendToRider(
                            w.getRiderId(),
                            "Withdrawal rejected",
                            "Your withdrawal request of ₹" + w.getAmount() + " was rejected. Balance unchanged.",
                            d,
                            NotificationType.RIDER_WITHDRAWAL_REJECTED);
                }
            }

            response.setData(toWithdrawalDto(w));
            response.setMessage(Boolean.TRUE.equals(dto.getApprove()) ? "Withdrawal approved" : "Withdrawal rejected");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<RiderCommissionConfigDTO> upsertCommissionConfig(Long adminUserId,
            RiderCommissionConfigDTO dto) {
        ApiResponse<RiderCommissionConfigDTO> response = new ApiResponse<>();
        try {
            if (dto == null) {
                throw new RuntimeException("body is required");
            }
            RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID)
                    .orElseGet(() -> {
                        RiderCommissionConfigEntity c = new RiderCommissionConfigEntity();
                        c.setId(COMMISSION_CONFIG_ID);
                        return c;
                    });
            if (dto.getOnlineCommissionPercent() != null) {
                validateCommissionPercent("onlineCommissionPercent", dto.getOnlineCommissionPercent());
                cfg.setOnlineCommissionPercent(dto.getOnlineCommissionPercent());
            }
            if (dto.getCodCashCommissionPercent() != null) {
                validateCommissionPercent("codCashCommissionPercent", dto.getCodCashCommissionPercent());
                cfg.setCodCashCommissionPercent(dto.getCodCashCommissionPercent());
            }
            if (dto.getCodQrCommissionPercent() != null) {
                validateCommissionPercent("codQrCommissionPercent", dto.getCodQrCommissionPercent());
                cfg.setCodQrCommissionPercent(dto.getCodQrCommissionPercent());
            }
            if (dto.getPeakSurgeBonusFlat() != null) {
                cfg.setPeakSurgeBonusFlat(dto.getPeakSurgeBonusFlat());
            }
            if (dto.getBaseFee() != null) {
                if (dto.getBaseFee() < 0) {
                    throw new RuntimeException("baseFee cannot be negative");
                }
                cfg.setBaseFee(dto.getBaseFee());
            }
            if (dto.getPerKmRate() != null) {
                if (dto.getPerKmRate() < 0) {
                    throw new RuntimeException("perKmRate cannot be negative");
                }
                cfg.setPerKmRate(dto.getPerKmRate());
            }
            cfg = riderCommissionConfigRepository.save(cfg);
            audit("COMMISSION_CONFIG_UPSERT", "ADMIN", adminUserId, "COMMISSION_CONFIG", cfg.getId(), dtoToMap(dto));
            response.setData(toCommissionDto(cfg));
            response.setMessage("Commission config saved");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<RiderCommissionConfigDTO> getCommissionConfig() {
        ApiResponse<RiderCommissionConfigDTO> response = new ApiResponse<>();
        try {
            RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID)
                    .orElse(null);
            if (cfg == null) {
                response.setData(new RiderCommissionConfigDTO());
            } else {
                response.setData(toCommissionDto(cfg));
            }
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void settleOrderDelivered(OrderEntity order, CodCollectionMode codMode, Double codCollectedAmount,
            Long actorUserId, String actorType) {
        if (order == null || order.getId() == null) {
            return;
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            return;
        }
        int expectedLegs = expectedSettlementLegCount(order);
        if (isOrderDeliveryWalletAlreadyCredited(order, expectedLegs)) {
            return;
        }
        if (expectedLegs == 1 && order.getRiderId() == null && order.getDeliveryRiderId() == null) {
            return;
        }

        ensureDefaultCommissionConfig();
        RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID).orElseThrow();

        double orderAmount = nz(order.getTotalAmount());
        PaymentType payType = order.getPaymentType();

        CodCollectionMode effectiveCodMode = codMode;
        if (payType == PaymentType.COD && effectiveCodMode == null) {
            effectiveCodMode = resolveSettlementCodMode(order);
        }
        double commissionPercent = resolveCommissionPercent(cfg, payType, effectiveCodMode);
        double commissionAmount = round2(orderAmount * (commissionPercent / 100.0));
        double baseRiderEarning = round2(orderAmount - commissionAmount);
        double peakBonusTotal = peakIncentiveService.resolveBonusForDeliveredOrder(order, Instant.now());
        double totalRiderPool = round2(baseRiderEarning + peakBonusTotal);
        if (totalRiderPool < -0.0001) {
            throw new RuntimeException("Invalid commission config: rider earning cannot be negative");
        }

        // D2D split: delivery rider always paid on destination hub → drop leg only.
        if (OutstationCodPolicy.hasSplitPickupAndDeliveryRiders(order)) {
            settleSplitOutstationOrderDelivered(
                    order,
                    payType,
                    effectiveCodMode,
                    orderAmount,
                    commissionPercent,
                    commissionAmount,
                    baseRiderEarning,
                    peakBonusTotal,
                    actorUserId,
                    actorType);
            ensureSplitDeliveryLegWalletCredited(order, payType, effectiveCodMode, actorUserId, actorType);
            return;
        }

        if (expectedLegs == 1) {
            if (OutstationCodPolicy.isDeliveryLegOnlyOrder(order)) {
                LegEarning deliveryLeg = computeDeliveryLegEarning(order, commissionPercent, peakBonusTotal);
                settleSingleRiderOrderDelivered(
                        order,
                        payType,
                        effectiveCodMode,
                        deliveryLeg.legAmount(),
                        commissionPercent,
                        deliveryLeg.commissionAmount(),
                        deliveryLeg.baseEarning(),
                        deliveryLeg.peakBonus(),
                        deliveryLeg.riderEarning(),
                        actorUserId,
                        actorType);
                return;
            }
            settleSingleRiderOrderDelivered(
                    order,
                    payType,
                    effectiveCodMode,
                    orderAmount,
                    commissionPercent,
                    commissionAmount,
                    baseRiderEarning,
                    peakBonusTotal,
                    Math.max(0.0, totalRiderPool),
                    actorUserId,
                    actorType);
            return;
        }

        settleSplitOutstationOrderDelivered(
                order,
                payType,
                effectiveCodMode,
                orderAmount,
                commissionPercent,
                commissionAmount,
                baseRiderEarning,
                peakBonusTotal,
                actorUserId,
                actorType);

        ensureSplitDeliveryLegWalletCredited(order, payType, effectiveCodMode, actorUserId, actorType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void ensureDeliverySettlementIfNeeded(OrderEntity order) {
        if (order == null || order.getId() == null || order.getStatus() != OrderStatus.DELIVERED) {
            return;
        }
        Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
        if (deliveryId == null) {
            return;
        }
        if (hasCompletedWalletCreditForOrder(deliveryId, order.getId())) {
            return;
        }
        log.info("DELIVERY_SETTLE_ENSURE orderId={} deliveryRider={}", order.getId(), deliveryId);
        creditOutstationDeliveryLegIfNeeded(order, deliveryId, "ENSURE");
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void repairPendingDeliveryWalletCredits(Long riderId) {
        if (riderId == null) {
            return;
        }
        try {
            List<OrderEntity> orders = orderRepository.findDeliveredOutstationOrdersForRider(
                    riderId, PageRequest.of(0, 100));
            for (OrderEntity order : orders) {
                try {
                    creditOutstationDeliveryLegIfNeeded(order, riderId, "REPAIR");
                } catch (RuntimeException ex) {
                    log.warn(
                            "DELIVERY_WALLET_REPAIR_FAILED orderId={} riderId={}: {}",
                            order.getId(),
                            riderId,
                            ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("DELIVERY_WALLET_REPAIR_ABORTED riderId={}: {}", riderId, ex.getMessage(), ex);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRiderWalletCreditedForOrder(Long riderId, Long orderId) {
        return hasCompletedWalletCreditForOrder(riderId, orderId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void settlePickupLegAtOriginHub(OrderEntity order, Long pickupRiderId) {
        if (order == null || order.getId() == null || pickupRiderId == null)
            return;
        if (order.getStatus() != OrderStatus.AT_ORIGIN_HUB)
            return;
        if (order.getServiceMode() != ServiceMode.OUTSTATION)
            return;

        // Idempotent: skip if already settled for this rider
        if (orderRiderFinancialRepository.findByOrderIdAndRiderId(order.getId(), pickupRiderId).isPresent())
            return;

        ensureDefaultCommissionConfig();
        RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID).orElseThrow();

        // Guard: outstation*Cost fields must be set for an accurate split.
        // Without them, OutstationPayableLegSplit falls back to full totalAmount as
        // pickupAmount which would over-credit the pickup rider.
        if (order.getOutstationPickupCost() == null
                || order.getOutstationHubCost() == null
                || order.getOutstationDropCost() == null) {
            log.warn("PICKUP_LEG_SETTLE_SKIP orderId={} pickupRider={}: outstation leg costs not persisted on order",
                    order.getId(), pickupRiderId);
            return;
        }

        OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
        double oaPick = leg.pickupAmount();
        double oaDrop = leg.lastMileAmount();
        log.info("PICKUP_LEG_SETTLE orderId={} pickupRider={} oaPick={} oaDrop={} totalAmount={}",
                order.getId(), pickupRiderId, oaPick, oaDrop, order.getTotalAmount());

        PaymentType payType = order.getPaymentType();
        CodCollectionMode codMode = payType == PaymentType.COD ? order.getCodCollectionMode() : null;
        double commissionPercent = resolveCommissionPercent(cfg, payType, codMode);
        double commPick = round2(oaPick * (commissionPercent / 100.0));
        double basePick = round2(Math.max(0.0, oaPick - commPick));
        double peakBonusTotal = peakIncentiveService.resolveBonusForDeliveredOrder(order, Instant.now());
        double riderLegDen = oaPick + oaDrop;
        double peakPick = riderLegDen > 0.0 ? round2(peakBonusTotal * (oaPick / riderLegDen)) : 0.0;
        double earnPick = round2(Math.max(0.0, basePick + peakPick));
        boolean pickupHoldsCod = OutstationCodPolicy.riderHoldsCodCash(order, pickupRiderId);
        double codCollected = payType == PaymentType.COD ? round2(nz(order.getCodCollectedAmount())) : 0.0;
        if (pickupHoldsCod && codCollected <= 0.0) {
            codCollected = round2(nz(order.getTotalAmount()));
        }

        OrderRiderFinancialEntity finP = new OrderRiderFinancialEntity();
        finP.setOrderId(order.getId());
        finP.setRiderId(pickupRiderId);
        finP.setOrderAmount(oaPick);
        finP.setCommissionPercentApplied(commissionPercent);
        finP.setCommissionAmount(commPick);
        finP.setSurgeBonusAmount(peakPick);
        finP.setRiderEarningAmount(earnPick);
        if (payType == PaymentType.ONLINE || !pickupHoldsCod) {
            finP.setCodCollectedAmount(null);
            finP.setCodCollectionMode(null);
            finP.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            finP.setSettledAt(Instant.now());
        } else {
            finP.setCodCollectedAmount(codCollected);
            finP.setCodCollectionMode(codMode);
            finP.setCodSettlementStatus(CodSettlementStatus.PENDING);
        }

        try {
            orderRiderFinancialRepository.saveAndFlush(finP);
        } catch (DataIntegrityViolationException ex) {
            log.info("PICKUP_LEG_SETTLE_RACE orderId={} pickupRider={} - row already exists, skipping",
                    order.getId(), pickupRiderId);
            return;
        }

        creditPickupLegEarning(order, pickupRiderId, payType, codMode, pickupHoldsCod, earnPick, oaPick,
                commissionPercent, commPick, basePick, peakPick, codCollected);

        log.info("PICKUP_LEG_SETTLED -> orderId={}, pickupRider={}, earnPick={}, codInHand={}",
                order.getId(), pickupRiderId, earnPick, pickupHoldsCod);
        audit("ORDER_SETTLE_PICKUP_LEG", "RIDER", pickupRiderId, "ORDER", order.getId(), Map.of(
                "pickupRiderId", pickupRiderId,
                "pickupEarning", earnPick,
                "paymentType", payType.name(),
                "codInHand", pickupHoldsCod));
    }

    private void creditPickupLegEarning(
            OrderEntity order,
            Long pickupRiderId,
            PaymentType payType,
            CodCollectionMode codMode,
            boolean pickupHoldsCod,
            double earnPick,
            double oaPick,
            double commissionPercent,
            double commPick,
            double basePick,
            double peakPick,
            double codCollected) {
        if (pickupHoldsCod) {
            recordCodCashCommissionPending(
                    order, pickupRiderId, earnPick, oaPick, commissionPercent, commPick, basePick, peakPick, codMode,
                    codCollected);
            notifyCodEarningRecorded(order.getId(), pickupRiderId, earnPick);
            return;
        }
        creditOnlineEarningToWallet(order, pickupRiderId, earnPick, oaPick, commissionPercent, commPick, basePick,
                peakPick);
        sendRiderEarningCreditedNotification(order.getId(), pickupRiderId, earnPick, payType);
    }

    private void settleSingleRiderOrderDelivered(
            OrderEntity order,
            PaymentType payType,
            CodCollectionMode codMode,
            double orderAmount,
            double commissionPercent,
            double commissionAmount,
            double baseRiderEarning,
            double peakBonus,
            double riderEarning,
            Long actorUserId,
            String actorType) {
        Long resolvedDeliveryRiderId = OutstationCodPolicy.resolveDeliveryRiderId(order);
        Long riderId = resolvedDeliveryRiderId != null ? resolvedDeliveryRiderId : order.getRiderId();
        log.info(
                "EARNING_CALCULATION -> riderId={}, orderId={}, paymentType={}, codMode={}, orderAmount={}, commissionPercent={}, commissionAmount={}, riderEarning={}",
                riderId,
                order.getId(),
                payType,
                codMode,
                orderAmount,
                commissionPercent,
                commissionAmount,
                riderEarning);

        OrderRiderFinancialEntity fin = new OrderRiderFinancialEntity();
        fin.setOrderId(order.getId());
        fin.setRiderId(riderId);
        fin.setOrderAmount(orderAmount);
        fin.setCommissionPercentApplied(commissionPercent);
        fin.setCommissionAmount(commissionAmount);
        fin.setSurgeBonusAmount(peakBonus);
        fin.setRiderEarningAmount(riderEarning);

        double collected = 0.0;
        if (payType == PaymentType.ONLINE) {
            if (!"PAID".equalsIgnoreCase(nzStr(order.getPaymentStatus()))) {
                throw new RuntimeException("ONLINE order must be PAID before settlement");
            }
            fin.setCodCollectedAmount(null);
            fin.setCodCollectionMode(null);
            fin.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            fin.setSettledAt(Instant.now());
        } else if (payType == PaymentType.COD) {
            if (OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, riderId)) {
                fin.setCodCollectedAmount(null);
                fin.setCodCollectionMode(null);
                fin.setCodSettlementStatus(CodSettlementStatus.SETTLED);
                fin.setSettledAt(Instant.now());
            } else {
                collected = round2(nz(order.getTotalAmount()));
                if (collected <= 0) {
                    throw new RuntimeException("Invalid order total for COD settlement");
                }
                fin.setCodCollectedAmount(collected);
                fin.setCodCollectionMode(codMode);
                if (codMode == CodCollectionMode.QR) {
                    fin.setCodSettlementStatus(CodSettlementStatus.SETTLED);
                    fin.setSettledAt(Instant.now());
                } else {
                    fin.setCodSettlementStatus(CodSettlementStatus.PENDING);
                }
            }
        } else {
            throw new RuntimeException("Unsupported payment type: " + payType);
        }

        try {
            orderRiderFinancialRepository.saveAndFlush(fin);
        } catch (DataIntegrityViolationException ex) {
            log.info("ORDER_SETTLE_RACE orderId={} - financial row already exists, skipping wallet mutations",
                    order.getId());
            return;
        }

        RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(riderId)
                .orElseGet(() -> riderWalletRepository.save(newWallet(riderId)));
        if (wallet.getCurrentBalance() < -0.0001) {
            throw new RuntimeException("Wallet in invalid negative state");
        }

        if (payType == PaymentType.ONLINE) {
            creditOnlineEarningToWallet(order, riderId, riderEarning, orderAmount, commissionPercent, commissionAmount,
                    baseRiderEarning, peakBonus);
            sendRiderEarningCreditedNotification(order.getId(), riderId, riderEarning, payType);
        } else if (payType == PaymentType.COD && (codMode == CodCollectionMode.QR
                || OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, riderId))) {
            creditOnlineEarningToWallet(order, riderId, riderEarning, orderAmount, commissionPercent, commissionAmount,
                    baseRiderEarning, peakBonus);
            sendRiderEarningCreditedNotification(order.getId(), riderId, riderEarning, payType);
        } else if (payType == PaymentType.COD) {
            recordCodCashCommissionPending(
                    order, riderId, riderEarning, orderAmount, commissionPercent, commissionAmount, baseRiderEarning,
                    peakBonus, codMode, collected);
        }

        audit("ORDER_SETTLE_DELIVERED", actorType, actorUserId, "ORDER", order.getId(), Map.of(
                "riderId", riderId,
                "paymentType", payType.name(),
                "riderEarning", riderEarning));

        if (payType == PaymentType.COD && codMode != CodCollectionMode.QR) {
            notifyCodEarningRecorded(order.getId(), riderId, riderEarning);
        }
    }

    private void settleSplitOutstationOrderDelivered(
            OrderEntity order,
            PaymentType payType,
            CodCollectionMode codMode,
            double orderAmount,
            double commissionPercent,
            double commissionAmount,
            double baseRiderEarning,
            double peakBonusTotal,
            Long actorUserId,
            String actorType) {
        Long pickupId = OutstationRiderLegPolicy.resolvePickupRiderId(order);
        Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
        if (pickupId == null || deliveryId == null) {
            throw new RuntimeException("OUTSTATION split settlement requires pickupRiderId and deliveryRiderId");
        }
        if (Objects.equals(pickupId, deliveryId)) {
            throw new RuntimeException("OUTSTATION split settlement requires distinct pickup and delivery riders");
        }

        OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
        double oaPick = leg.pickupAmount();
        LegEarning pickupLeg = computePickupLegEarning(oaPick, leg.lastMileAmount(), commissionPercent, peakBonusTotal);
        LegEarning deliveryLeg = computeDeliveryLegEarning(order, commissionPercent, peakBonusTotal);
        double commPick = pickupLeg.commissionAmount();
        double basePick = pickupLeg.baseEarning();
        double peakPick = pickupLeg.peakBonus();
        double earnPick = pickupLeg.riderEarning();
        double oaDrop = deliveryLeg.legAmount();
        double commDrop = deliveryLeg.commissionAmount();
        double baseDrop = deliveryLeg.baseEarning();
        double peakDrop = deliveryLeg.peakBonus();
        double earnDrop = deliveryLeg.riderEarning();

        if (payType == PaymentType.ONLINE && !"PAID".equalsIgnoreCase(nzStr(order.getPaymentStatus()))) {
            throw new RuntimeException("ONLINE order must be PAID before settlement");
        }
        double collected = payType == PaymentType.COD ? round2(orderAmount) : 0.0;
        if (payType == PaymentType.COD && collected <= 0) {
            throw new RuntimeException("Invalid order total for COD settlement");
        }

        OrderRiderFinancialEntity finP = new OrderRiderFinancialEntity();
        finP.setOrderId(order.getId());
        finP.setRiderId(pickupId);
        finP.setOrderAmount(oaPick);
        finP.setCommissionPercentApplied(commissionPercent);
        finP.setCommissionAmount(commPick);
        finP.setSurgeBonusAmount(peakPick);
        finP.setRiderEarningAmount(earnPick);
        if (payType == PaymentType.ONLINE) {
            finP.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            finP.setSettledAt(Instant.now());
        } else {
            finP.setCodCollectedAmount(null);
            finP.setCodCollectionMode(null);
            finP.setCodSettlementStatus(CodSettlementStatus.SETTLED);
        }

        OrderRiderFinancialEntity finD = new OrderRiderFinancialEntity();
        finD.setOrderId(order.getId());
        finD.setRiderId(deliveryId);
        finD.setOrderAmount(oaDrop);
        finD.setCommissionPercentApplied(commissionPercent);
        finD.setCommissionAmount(commDrop);
        finD.setSurgeBonusAmount(peakDrop);
        finD.setRiderEarningAmount(earnDrop);

        if (payType == PaymentType.ONLINE) {
            finD.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            finD.setSettledAt(Instant.now());
        } else {
            Long codCollectorRiderId = resolveCodCollectorRiderId(order);
            boolean pickupCollectsCod = codCollectorRiderId != null && Objects.equals(codCollectorRiderId, pickupId);
            if (pickupCollectsCod) {
                finP.setCodCollectedAmount(collected);
                finP.setCodCollectionMode(codMode);
                finP.setCodSettlementStatus(CodSettlementStatus.PENDING);
                finD.setCodCollectedAmount(null);
                finD.setCodCollectionMode(null);
                finD.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            } else {
                finD.setCodCollectedAmount(collected);
                finD.setCodCollectionMode(codMode);
                finD.setCodSettlementStatus(CodSettlementStatus.PENDING);
            }
        }

        // Pickup leg may already be settled when the pickup rider marked AT_ORIGIN_HUB.
        boolean pickupAlreadySettled = orderRiderFinancialRepository
                .findByOrderIdAndRiderId(order.getId(), pickupId).isPresent();

        if (!pickupAlreadySettled) {
            try {
                orderRiderFinancialRepository.saveAndFlush(finP);
            } catch (DataIntegrityViolationException ex) {
                log.info("ORDER_SETTLE_RACE orderId={} pickupRider={} - row already exists", order.getId(), pickupId);
                pickupAlreadySettled = true;
            }
        }

        boolean deliveryFinAlreadyRecorded = orderRiderFinancialRepository
                .findByOrderIdAndRiderId(order.getId(), deliveryId).isPresent();
        if (!deliveryFinAlreadyRecorded) {
            try {
                orderRiderFinancialRepository.saveAndFlush(finD);
            } catch (DataIntegrityViolationException ex) {
                log.info("ORDER_SETTLE_RACE orderId={} deliveryRider={} - financial row already exists",
                        order.getId(), deliveryId);
                deliveryFinAlreadyRecorded = true;
            }
        }

        if (!pickupAlreadySettled) {
            boolean pickupHoldsCod = payType == PaymentType.COD
                    && OutstationCodPolicy.riderHoldsCodCash(order, pickupId);
            creditPickupLegEarning(order, pickupId, payType, codMode, pickupHoldsCod, earnPick, oaPick,
                    commissionPercent, commPick, basePick, peakPick, collected);
        }

        // Delivery rider always receives last-mile earning on complete — independent of
        // pickup hub settlement.
        if (earnDrop <= 0.0001) {
            log.warn(
                    "DELIVERY_LEG_ZERO_EARNING orderId={} deliveryRider={} oaDrop={} pickupCost={} hubCost={} dropCost={}",
                    order.getId(),
                    deliveryId,
                    oaDrop,
                    order.getOutstationPickupCost(),
                    order.getOutstationHubCost(),
                    order.getOutstationDropCost());
        }
        creditOnlineEarningToWallet(order, deliveryId, earnDrop, oaDrop, commissionPercent, commDrop, baseDrop,
                peakDrop);

        log.info(
                "EARNING_SPLIT -> orderId={}, pickupRider={}, pickupEarning={} (preSettled={}), deliveryRider={}, deliveryEarning={}",
                order.getId(), pickupId, earnPick, pickupAlreadySettled, deliveryId, earnDrop);

        audit("ORDER_SETTLE_DELIVERED_SPLIT", actorType, actorUserId, "ORDER", order.getId(), Map.of(
                "pickupRiderId", pickupId,
                "deliveryRiderId", deliveryId,
                "pickupEarning", earnPick,
                "deliveryEarning", earnDrop,
                "paymentType", payType.name()));

        sendRiderEarningCreditedNotification(order.getId(), deliveryId, earnDrop, payType);

        ensureSplitDeliveryLegWalletCredited(order, payType, codMode, actorUserId, actorType);
    }

    /**
     * Safety net: after split D2D settlement, guarantee delivery rider wallet
     * credit exists.
     */
    private void ensureSplitDeliveryLegWalletCredited(
            OrderEntity order,
            PaymentType payType,
            CodCollectionMode codMode,
            Long actorUserId,
            String actorType) {
        Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
        Long actor = actorUserId != null ? actorUserId : deliveryId;
        creditOutstationDeliveryLegIfNeeded(order, actor, actorType);
    }

    /** Direct delivery-leg credit — bypasses {@link #settleOrderDelivered} early returns. */
    private void creditOutstationDeliveryLegIfNeeded(OrderEntity order, Long actorUserId, String actorType) {
        if (order == null || order.getId() == null || order.getStatus() != OrderStatus.DELIVERED) {
            return;
        }
        if (order.getServiceMode() != ServiceMode.OUTSTATION) {
            return;
        }
        Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
        if (deliveryId == null) {
            return;
        }
        if (!OutstationCodPolicy.needsDeliveryWalletCredit(order, deliveryId, this::hasCompletedWalletCreditForOrder)) {
            return;
        }
        log.warn(
                "DELIVERY_WALLET_CREDIT orderId={} deliveryRider={} actor={}",
                order.getId(),
                deliveryId,
                actorType);

        PaymentType payType = order.getPaymentType();
        CodCollectionMode codMode = resolveSettlementCodMode(order);
        ensureDefaultCommissionConfig();
        RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID).orElseThrow();
        double commissionPercent = resolveCommissionPercent(cfg, payType, codMode);
        double peakBonusTotal = peakIncentiveService.resolveBonusForDeliveredOrder(order, Instant.now());
        LegEarning deliveryLeg = computeDeliveryLegEarning(order, commissionPercent, peakBonusTotal);
        double oaDrop = deliveryLeg.legAmount();
        double earnDrop = deliveryLeg.riderEarning();

        if (!orderRiderFinancialRepository.findByOrderIdAndRiderId(order.getId(), deliveryId).isPresent()) {
            OrderRiderFinancialEntity finD = new OrderRiderFinancialEntity();
            finD.setOrderId(order.getId());
            finD.setRiderId(deliveryId);
            finD.setOrderAmount(oaDrop);
            finD.setCommissionPercentApplied(commissionPercent);
            finD.setCommissionAmount(deliveryLeg.commissionAmount());
            finD.setSurgeBonusAmount(deliveryLeg.peakBonus());
            finD.setRiderEarningAmount(earnDrop);
            finD.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            finD.setSettledAt(Instant.now());
            try {
                orderRiderFinancialRepository.saveAndFlush(finD);
            } catch (DataIntegrityViolationException ex) {
                log.info("DELIVERY_CREDIT fin row exists orderId={} deliveryRider={}", order.getId(), deliveryId);
            }
        }

        if (earnDrop <= 0.0001) {
            log.warn(
                    "DELIVERY_LEG_ZERO_EARNING orderId={} deliveryRider={} oaDrop={}",
                    order.getId(),
                    deliveryId,
                    oaDrop);
            return;
        }

        if (payType == PaymentType.COD
                && OutstationCodPolicy.riderHoldsCodCash(order, deliveryId)
                && !OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, deliveryId)) {
            double collected = round2(nz(order.getCodCollectedAmount()));
            if (collected <= 0) {
                collected = round2(nz(order.getTotalAmount()));
            }
            recordCodCashCommissionPending(
                    order,
                    deliveryId,
                    earnDrop,
                    oaDrop,
                    commissionPercent,
                    deliveryLeg.commissionAmount(),
                    deliveryLeg.baseEarning(),
                    deliveryLeg.peakBonus(),
                    codMode,
                    collected);
        } else {
            creditOnlineEarningToWallet(
                    order,
                    deliveryId,
                    earnDrop,
                    oaDrop,
                    commissionPercent,
                    deliveryLeg.commissionAmount(),
                    deliveryLeg.baseEarning(),
                    deliveryLeg.peakBonus());
            sendRiderEarningCreditedNotification(order.getId(), deliveryId, earnDrop, payType);
        }

        audit("ORDER_SETTLE_DELIVERY_REPAIR", actorType, actorUserId, "ORDER", order.getId(), Map.of(
                "deliveryRiderId", deliveryId,
                "deliveryEarning", earnDrop));
    }

    private CodCollectionMode resolveSettlementCodMode(OrderEntity order) {
        if (order == null || order.getPaymentType() != PaymentType.COD) {
            return null;
        }
        if (order.getCodCollectionMode() != null) {
            return order.getCodCollectionMode();
        }
        return CodCollectionMode.CASH;
    }

    private record LegEarning(
            double legAmount,
            double commissionAmount,
            double baseEarning,
            double peakBonus,
            double riderEarning) {
    }

    private LegEarning computeLegEarning(double legAmount, double commissionPercent, double peakBonus) {
        double comm = round2(legAmount * (commissionPercent / 100.0));
        double base = round2(Math.max(0.0, legAmount - comm));
        double earn = round2(Math.max(0.0, base + peakBonus));
        return new LegEarning(legAmount, comm, base, peakBonus, earn);
    }

    /** Destination hub → customer drop, commission deducted from drop leg only. */
    private LegEarning computeDeliveryLegEarning(OrderEntity order, double commissionPercent, double peakBonusTotal) {
        OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
        double oaDrop = leg.lastMileAmount();
        double oaPick = leg.pickupAmount();
        double riderLegDen = oaPick + oaDrop;
        double peakDrop = riderLegDen > 0.0 ? round2(peakBonusTotal * (oaDrop / riderLegDen)) : peakBonusTotal;
        return computeLegEarning(oaDrop, commissionPercent, peakDrop);
    }

    private LegEarning computePickupLegEarning(
            double oaPick, double oaDrop, double commissionPercent, double peakBonusTotal) {
        double riderLegDen = oaPick + oaDrop;
        double peakPick = riderLegDen > 0.0 ? round2(peakBonusTotal * (oaPick / riderLegDen)) : peakBonusTotal;
        return computeLegEarning(oaPick, commissionPercent, peakPick);
    }

    private boolean isOrderDeliveryWalletAlreadyCredited(OrderEntity order, int expectedLegs) {
        if (order == null || order.getId() == null) {
            return true;
        }
        Long deliveryId = OutstationCodPolicy.resolveDeliveryRiderId(order);
        if (order.getServiceMode() == ServiceMode.OUTSTATION && deliveryId != null) {
            if (OutstationCodPolicy.needsDeliveryWalletCredit(order, deliveryId, this::hasCompletedWalletCreditForOrder)) {
                return false;
            }
            if (OutstationCodPolicy.isOutstationLastMileRider(order, deliveryId)) {
                return hasCompletedWalletCreditForOrder(deliveryId, order.getId());
            }
        }
        if (OutstationCodPolicy.hasSplitPickupAndDeliveryRiders(order) || expectedLegs >= 2) {
            if (deliveryId == null) {
                return false;
            }
            return hasCompletedWalletCreditForOrder(deliveryId, order.getId());
        }
        if (OutstationCodPolicy.isDoorToDoor(order) && OutstationCodPolicy.isOutstation(order)) {
            Long pickupId = OutstationRiderLegPolicy.resolvePickupRiderId(order);
            if (pickupId != null && deliveryId != null && !Objects.equals(pickupId, deliveryId)) {
                return hasCompletedWalletCreditForOrder(deliveryId, order.getId());
            }
            if (pickupId != null && deliveryId == null) {
                return false;
            }
        }
        Long payeeRiderId = deliveryId != null ? deliveryId : order.getRiderId();
        if (payeeRiderId == null) {
            return false;
        }
        return hasCompletedWalletCreditForOrder(payeeRiderId, order.getId());
    }

    private boolean hasCompletedWalletCreditForOrder(Long riderId, Long orderId) {
        if (riderId == null || orderId == null) {
            return false;
        }
        return riderWalletTransactionRepository
                .findTopByRiderIdAndReferenceTypeAndReferenceIdAndStatusOrderByIdDesc(
                        riderId, WalletTxnReferenceType.ORDER, orderId, WalletTxnStatus.COMPLETED)
                .isPresent();
    }

    private void creditOnlineEarningToWallet(
            OrderEntity order,
            Long riderId,
            double riderEarning,
            double orderAmountPortion,
            double commissionPercent,
            double commissionAmountPortion,
            double basePortion,
            double peakPortion) {
        if (riderEarning <= 0.0001) {
            return;
        }
        if (hasCompletedWalletCreditForOrder(riderId, order.getId())) {
            log.info("WALLET_CREDIT_SKIP orderId={} riderId={} — earning already credited", order.getId(), riderId);
            return;
        }
        RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(riderId)
                .orElseGet(() -> riderWalletRepository.save(newWallet(riderId)));
        if (wallet.getCurrentBalance() < -0.0001) {
            throw new RuntimeException("Wallet in invalid negative state");
        }
        wallet.setTotalEarnings(round2(wallet.getTotalEarnings() + riderEarning));
        wallet.setCurrentBalance(round2(wallet.getCurrentBalance() + riderEarning));
        riderWalletRepository.save(wallet);

        RiderWalletTransactionEntity earnTxn = new RiderWalletTransactionEntity();
        earnTxn.setRiderId(riderId);
        earnTxn.setType(WalletTxnType.CREDIT);
        earnTxn.setAmount(riderEarning);
        earnTxn.setReferenceType(WalletTxnReferenceType.ORDER);
        earnTxn.setReferenceId(order.getId());
        earnTxn.setStatus(WalletTxnStatus.COMPLETED);
        earnTxn.setNote("Order delivered - rider earning credit");
        earnTxn.setMetadataJson(writeJson(buildEarningTxnMetadata(
                orderAmountPortion,
                PaymentType.ONLINE,
                commissionPercent,
                commissionAmountPortion,
                basePortion,
                peakPortion,
                riderEarning,
                null,
                null)));
        riderWalletTransactionRepository.save(earnTxn);
    }

    /**
     * COD cash: rider keeps earnings in pocket; only platform commission is tracked
     * for hub deposit.
     * Does not credit withdrawable wallet balance.
     */
    private void recordCodCashCommissionPending(
            OrderEntity order,
            Long riderId,
            double riderEarning,
            double orderAmountPortion,
            double commissionPercent,
            double commissionAmountPortion,
            double basePortion,
            double peakPortion,
            CodCollectionMode codMode,
            double collected) {
        if (commissionAmountPortion <= 0.0001) {
            return;
        }
        RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(riderId)
                .orElseGet(() -> riderWalletRepository.save(newWallet(riderId)));
        double pendingBefore = round2(nz(wallet.getCodPendingAmount()));
        wallet.setTotalEarnings(round2(wallet.getTotalEarnings() + riderEarning));
        wallet.setCodPendingAmount(round2(pendingBefore + commissionAmountPortion));
        riderWalletRepository.save(wallet);

        log.info(
                "COD_COMMISSION_PENDING -> orderId={}, riderId={}, commission={}, codPending={}, collectedCash={}",
                order.getId(),
                riderId,
                commissionAmountPortion,
                wallet.getCodPendingAmount(),
                collected);

        maybeNotifyCodHandoverThresholds(riderId, pendingBefore, wallet.getCodPendingAmount());
    }

    private void notifyCodEarningRecorded(Long orderId, Long riderId, double riderEarning) {
        if (!notificationDedupService.tryAcquire("rider-cod-earning-recorded:" + orderId + ":" + riderId)) {
            return;
        }
        Map<String, String> data = new HashMap<>(
                NotificationService.baseData(orderId, OrderStatus.DELIVERED.name(),
                        NotificationType.RIDER_WALLET_EARNING_CREDITED));
        data.put("riderEarning", String.valueOf(riderEarning));
        data.put("paymentType", PaymentType.COD.name());
        data.put("cashInHand", "true");
        String body = "Rs. " + String.format("%.2f", riderEarning)
                + " recorded for order " + orderId + " (cash — keep in pocket).";
        notificationService.sendToRider(riderId, "Earning recorded", body, data,
                NotificationType.RIDER_WALLET_EARNING_CREDITED);
    }

    private void maybeNotifyCodHandoverThresholds(Long riderId, double pendingBefore, double pendingAfter) {
        double limit = resolveHandoverLimit(riderId);
        if (limit <= 0) {
            return;
        }
        double warnAt = round2(limit * COD_HANDOVER_WARNING_RATIO);
        if (pendingBefore + 0.0001 < warnAt && pendingAfter + 0.0001 >= warnAt && pendingAfter + 0.0001 < limit) {
            sendCodHandoverNotification(riderId, pendingAfter, limit, NotificationType.RIDER_COD_HANDOVER_WARNING,
                    "COD deposit reminder",
                    "Rs. " + String.format("%.2f", pendingAfter) + " commission to deposit at hub (limit Rs. "
                            + String.format("%.2f", limit) + ").");
        }
        if (pendingBefore + 0.0001 < limit && pendingAfter + 0.0001 >= limit) {
            sendCodHandoverNotification(riderId, pendingAfter, limit, NotificationType.RIDER_COD_HANDOVER_BLOCKED,
                    "Orders paused — deposit COD commission",
                    "Deposit Rs. " + String.format("%.2f", pendingAfter)
                            + " at hub to receive new orders (COD and online).");
        }
    }

    private void sendCodHandoverNotification(
            Long riderId,
            double pending,
            double limit,
            NotificationType type,
            String title,
            String body) {
        String dedupeKey = type.name() + ":" + riderId + ":" + (long) pending + ":" + (long) limit;
        if (!notificationDedupService.tryAcquire(dedupeKey)) {
            return;
        }
        Map<String, String> data = new HashMap<>(NotificationService.baseData(null, null, type));
        data.put("commissionPending", String.valueOf(pending));
        data.put("handoverLimit", String.valueOf(limit));
        notificationService.sendToRider(riderId, title, body, data, type);
    }

    private void sendRiderEarningCreditedNotification(Long orderId, Long riderId, double riderEarning,
            PaymentType payType) {
        if (notificationDedupService.tryAcquire("rider-wallet-earning:" + orderId + ":" + riderId)) {
            Map<String, String> earnData = new HashMap<>(
                    NotificationService.baseData(
                            orderId,
                            OrderStatus.DELIVERED.name(),
                            NotificationType.RIDER_WALLET_EARNING_CREDITED));
            earnData.put("riderEarning", String.valueOf(riderEarning));
            earnData.put("paymentType", payType.name());
            String earnBody = "Rs. " + String.format("%.2f", riderEarning) + " credited for order " + orderId + ".";
            notificationService.sendToRider(
                    riderId,
                    "Earning credited",
                    earnBody,
                    earnData,
                    NotificationType.RIDER_WALLET_EARNING_CREDITED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public double estimateRiderEarningForOrder(OrderEntity order) {
        if (order == null) {
            return 0.0;
        }
        RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID).orElse(null);
        if (cfg == null) {
            return 0.0;
        }
        PaymentType payType = order.getPaymentType();
        if (payType == null) {
            return 0.0;
        }
        double orderAmount = nz(order.getTotalAmount());
        CodCollectionMode codMode = payType == PaymentType.COD ? order.getCodCollectionMode() : null;
        double commissionPercent = resolveCommissionPercent(cfg, payType, codMode);
        if (order.getServiceMode() == com.youdash.model.ServiceMode.OUTSTATION) {
            OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
            Long pRid = order.getPickupRiderId();
            Long dRid = order.getDeliveryRiderId();
            boolean split = pRid != null && dRid != null && !pRid.equals(dRid);
            double pickupNet = round2(
                    Math.max(0.0, leg.pickupAmount() - (leg.pickupAmount() * (commissionPercent / 100.0))));
            double dropNet = round2(
                    Math.max(0.0, leg.lastMileAmount() - (leg.lastMileAmount() * (commissionPercent / 100.0))));
            if (split) {
                return round2(pickupNet + dropNet);
            }
            if (OutstationCodPolicy.isDeliveryLegOnlyOrder(order)
                    || OutstationCodPolicy.hasSplitPickupAndDeliveryRiders(order)) {
                return dropNet;
            }
            // Pre-assignment or single rider: show pickup leg earning as the dispatch
            // estimate
            return pickupNet;
        }
        double commissionAmount = round2(orderAmount * (commissionPercent / 100.0));
        return round2(Math.max(0.0, orderAmount - commissionAmount));
    }

    @Override
    @Transactional(readOnly = true)
    public double resolveRiderEarningForOrder(OrderEntity order) {
        if (order == null || order.getId() == null) {
            return 0.0;
        }
        List<OrderRiderFinancialEntity> rows = orderRiderFinancialRepository.findAllByOrderId(order.getId());
        if (!rows.isEmpty()) {
            double sum = 0.0;
            for (OrderRiderFinancialEntity r : rows) {
                sum += nz(r.getRiderEarningAmount());
            }
            return round2(sum);
        }
        return estimateRiderEarningForOrder(order);
    }

    @Override
    @Transactional(readOnly = true)
    public double resolveRiderEarningForOrder(OrderEntity order, Long riderId) {
        if (order == null || order.getId() == null || riderId == null) {
            return 0.0;
        }
        Optional<OrderRiderFinancialEntity> finOpt = orderRiderFinancialRepository
                .findByOrderIdAndRiderId(order.getId(), riderId);
        if (finOpt.isPresent()) {
            return round2(nz(finOpt.get().getRiderEarningAmount()));
        }
        return estimateLegEarningForRider(order, riderId);
    }

    private double estimateLegEarningForRider(OrderEntity order, Long riderId) {
        if (order == null || riderId == null) {
            return 0.0;
        }
        RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID).orElse(null);
        if (cfg == null) {
            return 0.0;
        }
        PaymentType payType = order.getPaymentType();
        if (payType == null) {
            return 0.0;
        }
        double orderAmount = nz(order.getTotalAmount());
        CodCollectionMode codMode = payType == PaymentType.COD ? order.getCodCollectionMode() : null;
        double commissionPercent = resolveCommissionPercent(cfg, payType, codMode);
        return estimateLegEarningFromOrderAmount(orderAmount, order, riderId, commissionPercent);
    }

    private static double estimateLegEarningFromOrderAmount(
            double orderAmount,
            OrderEntity order,
            Long riderId,
            double commissionPercent) {
        if (orderAmount <= 0.0 || order == null || riderId == null) {
            return 0.0;
        }
        if (order.getServiceMode() != com.youdash.model.ServiceMode.OUTSTATION) {
            if (!Objects.equals(riderId, order.getRiderId())) {
                return 0.0;
            }
            return round2(Math.max(0.0, orderAmount - (orderAmount * (commissionPercent / 100.0))));
        }
        Long pRid = order.getPickupRiderId();
        Long dRid = order.getDeliveryRiderId();
        boolean split = pRid != null && dRid != null && !pRid.equals(dRid);
        OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
        double pickupNet = round2(
                Math.max(0.0, leg.pickupAmount() - (leg.pickupAmount() * (commissionPercent / 100.0))));
        double dropNet = round2(Math.max(0.0,
                leg.lastMileAmount() - (leg.lastMileAmount() * (commissionPercent / 100.0))));
        if (!split) {
            // During phased assignment (only pickup rider assigned first, delivery rider
            // later),
            // keep earnings leg-based for whichever role is currently assigned.
            if (pRid != null && Objects.equals(riderId, pRid)) {
                return pickupNet;
            }
            if (dRid != null && Objects.equals(riderId, dRid)) {
                return dropNet;
            }
            if (Objects.equals(riderId, order.getRiderId())) {
                if (dRid == null) {
                    return pickupNet;
                }
                return dropNet;
            }
            if (Objects.equals(riderId, order.getPickupRiderId())) {
                return pickupNet;
            }
            if (Objects.equals(riderId, order.getDeliveryRiderId())) {
                return dropNet;
            }
            return 0.0;
        }
        if (Objects.equals(riderId, order.getPickupRiderId())) {
            return pickupNet;
        }
        if (Objects.equals(riderId, order.getDeliveryRiderId())) {
            return dropNet;
        }
        return 0.0;
    }

    private static double resolveCommissionPercent(RiderCommissionConfigEntity cfg, PaymentType payType,
            CodCollectionMode codMode) {
        if (cfg == null) {
            return 0.0;
        }
        if (payType == PaymentType.ONLINE) {
            return validPercentOrZero(cfg.getOnlineCommissionPercent());
        }
        if (payType == PaymentType.COD) {
            if (codMode == CodCollectionMode.QR) {
                return validPercentOrZero(cfg.getCodQrCommissionPercent());
            }
            // COD mode is unknown at dispatch-time. Default to CASH config for estimation.
            return validPercentOrZero(cfg.getCodCashCommissionPercent());
        }
        return 0.0;
    }

    private static double validPercentOrZero(Double raw) {
        double v = nz(raw);
        if (v < 0.0 || v > 100.0) {
            throw new RuntimeException("Commission percent must be between 0 and 100");
        }
        return v;
    }

    private static Long resolveCodCollectorRiderId(OrderEntity order) {
        return com.youdash.util.OutstationCodPolicy.resolveCodCollectorRiderId(order);
    }

    private static void validateCommissionPercent(String field, Double value) {
        if (value == null) {
            return;
        }
        if (value < 0.0 || value > 100.0) {
            throw new RuntimeException(field + " must be between 0 and 100");
        }
    }

    private Map<String, Object> buildEarningTxnMetadata(
            double orderAmount,
            PaymentType payType,
            double commissionPercent,
            double commissionAmount,
            double baseRiderEarning,
            double peakBonus,
            double riderEarning,
            CodCollectionMode codMode,
            Double codCollected) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("calculationType", "PERCENTAGE_BASED");
        m.put("commissionPercent", commissionPercent);
        m.put("commissionAmount", commissionAmount);
        m.put("formula", "riderEarning = orderAmount - (orderAmount * commissionPercent / 100)");
        m.put("baseRiderEarning", baseRiderEarning);
        m.put("peakBonus", peakBonus);
        m.put("calculatedEarning", riderEarning);
        m.put("riderEarning", riderEarning);
        m.put("orderAmount", orderAmount);
        m.put("paymentType", payType.name());
        if (codMode != null && codCollected != null) {
            m.put("codCollectionMode", codMode.name());
            m.put("codCollectedAmount", codCollected);
        }
        return m;
    }

    @Override
    @Transactional
    public void ensureDefaultCommissionConfig() {
        if (riderCommissionConfigRepository.existsById(COMMISSION_CONFIG_ID)) {
            return;
        }
        RiderCommissionConfigEntity cfg = new RiderCommissionConfigEntity();
        cfg.setId(COMMISSION_CONFIG_ID);
        cfg.setOnlineCommissionPercent(10.0);
        cfg.setCodCashCommissionPercent(10.0);
        cfg.setCodQrCommissionPercent(8.0);
        cfg.setPeakSurgeBonusFlat(0.0);
        cfg.setBaseFee(1.0);
        cfg.setPerKmRate(1.0);
        riderCommissionConfigRepository.save(cfg);
    }

    @Override
    @Transactional
    public ApiResponse<String> adminSettleCod(Long adminUserId, AdminCodSettleRequestDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getOrderId() == null || dto.getOrderId().isBlank()) {
                throw new RuntimeException("orderId is required");
            }
            if (dto.getAmount() == null || dto.getAmount() <= 0) {
                throw new RuntimeException("amount is required");
            }

            OrderEntity order = resolveOrderByIdOrReference(dto.getOrderId().trim());
            if (order.getPaymentType() != PaymentType.COD) {
                throw new RuntimeException("Order is not COD");
            }
            Long codRiderId = resolveCodCollectorRiderId(order);
            if (codRiderId == null) {
                throw new RuntimeException("Order has no COD collector rider for settlement");
            }

            Optional<OrderRiderFinancialEntity> finRow = orderRiderFinancialRepository
                    .findByOrderIdAndRiderId(order.getId(), codRiderId);
            if (finRow.isEmpty()) {
                throw new RuntimeException("Financials not found for order (complete delivery first)");
            }
            OrderRiderFinancialEntity fin = finRow.get();

            double expected = round2(nz(fin.getCommissionAmount()));
            double amt = round2(dto.getAmount());
            if (expected <= 0) {
                throw new RuntimeException("No COD commission recorded for this order");
            }
            if (Math.abs(expected - amt) > 0.01) {
                throw new RuntimeException("amount must match commission for this order");
            }
            if (fin.getCodSettlementStatus() == CodSettlementStatus.SETTLED) {
                response.setData("OK");
                response.setMessage("COD already settled");
                response.setMessageKey("SUCCESS");
                response.setSuccess(true);
                response.setStatus(200);
                return response;
            }

            Optional<RiderWalletEntity> walletOpt = riderWalletRepository.lockByRiderId(codRiderId);
            if (walletOpt.isEmpty()) {
                throw new RuntimeException("Wallet not found");
            }
            RiderWalletEntity wallet = walletOpt.get();
            if (wallet.getCodPendingAmount() + 0.0001 < amt) {
                throw new RuntimeException("COD commission pending mismatch");
            }

            wallet.setCodPendingAmount(round2(wallet.getCodPendingAmount() - amt));
            riderWalletRepository.save(wallet);

            fin.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            fin.setSettledAt(Instant.now());
            orderRiderFinancialRepository.save(fin);

            settleOrderCodStatusIfComplete(order);

            Map<String, Object> codAudit = new HashMap<>();
            codAudit.put("riderId", codRiderId);
            codAudit.put("amount", amt);
            audit("COD_SETTLE", "ADMIN", adminUserId, "ORDER", order.getId(), codAudit);

            response.setData("OK");
            response.setMessage("COD commission settled");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRiderDispatchBlocked(Long riderId) {
        if (riderId == null) {
            return false;
        }
        RiderWalletEntity w = riderWalletRepository.findByRiderId(riderId).orElse(null);
        double pending = w != null ? round2(nz(w.getCodPendingAmount())) : 0.0;
        return isDispatchBlocked(pending, resolveHandoverLimit(riderId));
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminCodRiderSummaryDTO>> adminListCodRiders(String statusFilter, String search) {
        ApiResponse<List<AdminCodRiderSummaryDTO>> response = new ApiResponse<>();
        try {
            String q = search != null ? search.trim().toLowerCase() : "";
            String status = statusFilter != null ? statusFilter.trim().toUpperCase() : "";
            List<AdminCodRiderSummaryDTO> rows = riderWalletRepository.findAll().stream()
                    .filter(w -> w.getRiderId() != null)
                    .map(w -> buildAdminCodRiderSummary(w.getRiderId(), w))
                    .filter(s -> s.getCommissionPending() > 0.0001)
                    .filter(s -> status.isEmpty()
                            || "ALL".equals(status)
                            || status.equals(s.getHandoverStatus()))
                    .filter(s -> q.isEmpty() || matchesCodRiderSearch(s, q))
                    .sorted((a, b) -> Double.compare(b.getCommissionPending(), a.getCommissionPending()))
                    .collect(Collectors.toList());
            response.setData(rows);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(rows.size());
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<AdminCodRiderDetailDTO> adminGetCodRiderDetail(Long riderId) {
        ApiResponse<AdminCodRiderDetailDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("riderId is required");
            }
            RiderWalletEntity wallet = getOrCreateWallet(riderId);
            AdminCodRiderDetailDTO detail = new AdminCodRiderDetailDTO();
            detail.setSummary(buildAdminCodRiderSummary(riderId, wallet));
            detail.setOpenLines(loadOpenCodLines(riderId));
            detail.setRecentDeposits(loadRecentDeposits(riderId, 20));
            response.setData(detail);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<String> adminConfirmCodDeposit(Long adminUserId, AdminCodDepositRequestDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getRiderId() == null) {
                throw new RuntimeException("riderId is required");
            }
            if (dto.getAmount() == null || dto.getAmount() <= 0) {
                throw new RuntimeException("amount is required");
            }
            Long riderId = dto.getRiderId();
            double amt = round2(dto.getAmount());
            RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(riderId)
                    .orElseThrow(() -> new RuntimeException("Wallet not found"));
            double pending = round2(nz(wallet.getCodPendingAmount()));
            if (pending <= 0) {
                throw new RuntimeException("No commission pending for this rider");
            }
            if (amt - pending > 0.01) {
                throw new RuntimeException("amount cannot exceed pending commission (" + pending + ")");
            }

            CodDepositEntity deposit = new CodDepositEntity();
            deposit.setRiderId(riderId);
            deposit.setHubId(dto.getHubId());
            deposit.setAmount(amt);
            deposit.setAdminUserId(adminUserId);
            deposit.setNote(dto.getNote());
            codDepositRepository.saveAndFlush(deposit);

            double remaining = amt;
            List<OrderRiderFinancialEntity> openLines = orderRiderFinancialRepository
                    .findByRiderIdAndCodSettlementStatusOrderByCreatedAtAsc(riderId, CodSettlementStatus.PENDING);
            for (OrderRiderFinancialEntity fin : openLines) {
                if (remaining <= 0.0001) {
                    break;
                }
                double lineComm = round2(nz(fin.getCommissionAmount()));
                if (lineComm <= 0) {
                    continue;
                }
                if (remaining + 0.0001 < lineComm) {
                    break;
                }
                fin.setCodSettlementStatus(CodSettlementStatus.SETTLED);
                fin.setSettledAt(Instant.now());
                fin.setCodDepositId(deposit.getId());
                orderRiderFinancialRepository.save(fin);
                remaining = round2(remaining - lineComm);
                OrderEntity order = orderRepository.findById(fin.getOrderId()).orElse(null);
                if (order != null) {
                    settleOrderCodStatusIfComplete(order);
                }
            }

            wallet.setCodPendingAmount(round2(pending - amt));
            riderWalletRepository.save(wallet);

            audit("COD_DEPOSIT", "ADMIN", adminUserId, "RIDER", riderId, Map.of(
                    "depositId", deposit.getId(),
                    "amount", amt,
                    "hubId", dto.getHubId()));

            if (notificationDedupService.tryAcquire("rider-cod-deposit:" + deposit.getId())) {
                Map<String, String> codData = new HashMap<>(
                        NotificationService.baseData(null, null, NotificationType.RIDER_COD_DEPOSIT_CONFIRMED));
                codData.put("settledAmount", String.valueOf(amt));
                codData.put("commissionPending", String.valueOf(wallet.getCodPendingAmount()));
                String body = "Rs. " + String.format("%.2f", amt) + " commission received at hub.";
                if (wallet.getCodPendingAmount() <= resolveHandoverLimit(riderId) - 0.0001) {
                    body += " You can accept orders again.";
                }
                notificationService.sendToRider(
                        riderId,
                        "COD deposit confirmed",
                        body,
                        codData,
                        NotificationType.RIDER_COD_DEPOSIT_CONFIRMED);
            }

            response.setData("OK");
            response.setMessage("Deposit recorded");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
            response.setStatus(400);
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<RiderResponseDTO> adminUpdateCodHandoverLimit(Long riderId,
            AdminCodHandoverLimitRequestDTO dto) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("riderId is required");
            }
            if (dto == null || dto.getCodHandoverLimit() == null || dto.getCodHandoverLimit() <= 0) {
                throw new RuntimeException("codHandoverLimit must be positive");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found"));
            rider.setCodHandoverLimit(round2(dto.getCodHandoverLimit()));
            riderRepository.save(rider);
            response.setMessage("COD handover limit updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
            response.setStatus(400);
        }
        return response;
    }

    private void settleOrderCodStatusIfComplete(OrderEntity order) {
        if (order == null || order.getId() == null) {
            return;
        }
        List<OrderRiderFinancialEntity> rows = orderRiderFinancialRepository.findAllByOrderId(order.getId());
        boolean anyCodPending = rows.stream()
                .anyMatch(r -> r.getCodSettlementStatus() == CodSettlementStatus.PENDING
                        && nz(r.getCommissionAmount()) > 0);
        if (!anyCodPending) {
            order.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            orderRepository.save(order);
        }
    }

    private AdminCodRiderSummaryDTO buildAdminCodRiderSummary(Long riderId, RiderWalletEntity wallet) {
        RiderEntity rider = riderRepository.findById(riderId).orElse(null);
        double pending = round2(nz(wallet.getCodPendingAmount()));
        double limit = resolveHandoverLimit(riderId);
        AdminCodRiderSummaryDTO s = new AdminCodRiderSummaryDTO();
        s.setRiderId(riderId);
        if (rider != null) {
            s.setRiderName(rider.getName());
            s.setRiderPhone(rider.getPhone());
            s.setRiderPublicId(rider.getPublicId());
        }
        s.setCommissionPending(pending);
        s.setHandoverLimit(limit);
        s.setHandoverStatus(resolveHandoverStatus(pending, limit));
        s.setDispatchBlocked(isDispatchBlocked(pending, limit));
        s.setOpenLineCount(orderRiderFinancialRepository.countByRiderIdAndCodSettlementStatus(
                riderId, CodSettlementStatus.PENDING));
        List<CodDepositEntity> last = codDepositRepository.findByRiderIdOrderByCreatedAtDesc(
                riderId, PageRequest.of(0, 1));
        if (!last.isEmpty()) {
            CodDepositEntity d = last.get(0);
            s.setLastDepositAmount(round2(d.getAmount()));
            s.setLastDepositAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        }
        return s;
    }

    private List<AdminCodOpenLineDTO> loadOpenCodLines(Long riderId) {
        return orderRiderFinancialRepository
                .findByRiderIdAndCodSettlementStatusOrderByCreatedAtAsc(riderId, CodSettlementStatus.PENDING)
                .stream()
                .filter(f -> nz(f.getCommissionAmount()) > 0)
                .map(f -> {
                    AdminCodOpenLineDTO line = new AdminCodOpenLineDTO();
                    line.setOrderId(f.getOrderId());
                    OrderEntity o = orderRepository.findById(f.getOrderId()).orElse(null);
                    if (o != null) {
                        line.setDisplayOrderId(o.getDisplayOrderId());
                    }
                    line.setOrderAmount(f.getOrderAmount());
                    line.setCommissionAmount(f.getCommissionAmount());
                    line.setRiderEarningAmount(f.getRiderEarningAmount());
                    line.setCommissionPercentApplied(f.getCommissionPercentApplied());
                    line.setCodCollectionMode(
                            f.getCodCollectionMode() != null ? f.getCodCollectionMode().name() : null);
                    line.setDeliveredAt(f.getCreatedAt() != null ? f.getCreatedAt().toString() : null);
                    return line;
                })
                .collect(Collectors.toList());
    }

    private List<AdminCodDepositHistoryDTO> loadRecentDeposits(Long riderId, int limit) {
        return codDepositRepository.findByRiderIdOrderByCreatedAtDesc(riderId, PageRequest.of(0, limit)).stream()
                .map(d -> {
                    AdminCodDepositHistoryDTO h = new AdminCodDepositHistoryDTO();
                    h.setDepositId(d.getId());
                    h.setAmount(d.getAmount());
                    h.setHubId(d.getHubId());
                    h.setNote(d.getNote());
                    h.setCreatedAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
                    return h;
                })
                .collect(Collectors.toList());
    }

    private static boolean matchesCodRiderSearch(AdminCodRiderSummaryDTO s, String q) {
        return containsIgnoreCase(s.getRiderName(), q)
                || containsIgnoreCase(s.getRiderPhone(), q)
                || containsIgnoreCase(s.getRiderPublicId(), q)
                || String.valueOf(s.getRiderId()).contains(q);
    }

    private static boolean containsIgnoreCase(String value, String q) {
        return value != null && value.toLowerCase().contains(q);
    }

    private double resolveHandoverLimit(Long riderId) {
        if (riderId == null) {
            return DEFAULT_COD_HANDOVER_LIMIT;
        }
        return riderRepository.findById(riderId)
                .map(RiderEntity::getCodHandoverLimit)
                .filter(l -> l != null && l > 0)
                .orElse(DEFAULT_COD_HANDOVER_LIMIT);
    }

    private static String resolveHandoverStatus(double pending, double limit) {
        if (limit <= 0) {
            return "OK";
        }
        if (pending + 0.0001 >= limit) {
            return "BLOCKED";
        }
        if (pending + 0.0001 >= limit * COD_HANDOVER_WARNING_RATIO) {
            return "WARNING";
        }
        return "OK";
    }

    private static boolean isDispatchBlocked(double pending, double limit) {
        return limit > 0 && pending + 0.0001 >= limit;
    }

    private OrderEntity resolveOrderByIdOrReference(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("orderId is required");
        }
        String s = raw.trim();
        if (s.regionMatches(true, 0, "YP-", 0, 3)) {
            return orderRepository.findByDisplayOrderId(s)
                    .orElseThrow(() -> new RuntimeException("Order not found with display id: " + s));
        }
        try {
            long id = Long.parseLong(s);
            return orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid orderId: " + s);
        }
    }

    private RiderWalletEntity getOrCreateWallet(Long riderId) {
        return riderWalletRepository.findByRiderId(riderId)
                .orElseGet(() -> riderWalletRepository.save(newWallet(riderId)));
    }

    private RiderWalletEntity newWallet(Long riderId) {
        RiderWalletEntity w = new RiderWalletEntity();
        w.setRiderId(riderId);
        w.setCurrentBalance(0.0);
        w.setTotalEarnings(0.0);
        w.setTotalWithdrawn(0.0);
        w.setCodPendingAmount(0.0);
        w.setWithdrawalPendingAmount(0.0);
        return w;
    }

    private RiderWalletTransactionDTO toTxnDto(RiderWalletTransactionEntity e) {
        RiderWalletTransactionDTO d = new RiderWalletTransactionDTO();
        d.setId(e.getId());
        d.setType(e.getType() != null ? e.getType().name() : null);
        d.setAmount(e.getAmount());
        d.setReferenceType(e.getReferenceType() != null ? e.getReferenceType().name() : null);
        d.setReferenceId(e.getReferenceId());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setNote(e.getNote());
        d.setMetadataJson(e.getMetadataJson());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return d;
    }

    private boolean isRiderVisibleTransaction(RiderWalletTransactionEntity e) {
        if (e == null) {
            return false;
        }
        String note = nzStr(e.getNote());
        // This row records COD cash collection liability movement, not rider earning.
        if ("COD collected by rider - pending settlement".equals(note)) {
            return false;
        }
        return true;
    }

    private double sumRiderEarningsBetween(Long riderId, Instant fromTs, Instant toTs) {
        Double sum = orderRiderFinancialRepository.sumRiderEarningsBetween(riderId, fromTs, toTs);
        return round2(nz(sum));
    }

    private RiderWithdrawalDTO toWithdrawalDto(RiderWithdrawalEntity e) {
        RiderWithdrawalDTO d = new RiderWithdrawalDTO();
        d.setId(e.getId());
        d.setRiderId(e.getRiderId());
        d.setAmount(e.getAmount());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setAccountHolderName(e.getBankAccountName());
        d.setAccountNumber(e.getBankAccountNumber());
        d.setIfsc(e.getBankIfsc());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return d;
    }

    private RiderCommissionConfigDTO toCommissionDto(RiderCommissionConfigEntity e) {
        RiderCommissionConfigDTO d = new RiderCommissionConfigDTO();
        d.setOnlineCommissionPercent(e.getOnlineCommissionPercent());
        d.setCodCashCommissionPercent(e.getCodCashCommissionPercent());
        d.setCodQrCommissionPercent(e.getCodQrCommissionPercent());
        d.setPeakSurgeBonusFlat(e.getPeakSurgeBonusFlat());
        d.setBaseFee(e.getBaseFee());
        d.setPerKmRate(e.getPerKmRate());
        return d;
    }

    private Map<String, Object> dtoToMap(RiderCommissionConfigDTO dto) {
        Map<String, Object> m = new HashMap<>();
        m.put("onlineCommissionPercent", dto.getOnlineCommissionPercent());
        m.put("codCashCommissionPercent", dto.getCodCashCommissionPercent());
        m.put("codQrCommissionPercent", dto.getCodQrCommissionPercent());
        m.put("peakSurgeBonusFlat", dto.getPeakSurgeBonusFlat());
        m.put("baseFee", dto.getBaseFee());
        m.put("perKmRate", dto.getPerKmRate());
        return m;
    }

    private void audit(String action, String actorType, Long actorId, String entityType, Long entityId,
            Object payload) {
        try {

            FinAuditLogEntity log = new FinAuditLogEntity();
            log.setAction(action);
            log.setActorType(actorType);
            log.setActorId(actorId);
            log.setEntityType(entityType);
            log.setEntityId(entityId);
            log.setPayloadJson(writeJson(payload));
            finAuditLogRepository.save(log);
        } catch (Exception ignored) {
            // never fail financial flow due to audit persistence issues
        }
    }

    private String writeJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return null;
        }
    }

    private static void setErr(ApiResponse<?> r, String msg) {
        r.setMessage(msg);
        r.setMessageKey("ERROR");
        r.setSuccess(false);
        r.setStatus(500);
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static String nzStr(String s) {
        return s == null ? "" : s;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

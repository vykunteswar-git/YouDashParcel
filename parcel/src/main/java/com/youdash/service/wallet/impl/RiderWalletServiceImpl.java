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
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.wallet.AdminWithdrawalApproveDTO;
import com.youdash.dto.wallet.AdminCodSettleRequestDTO;
import com.youdash.dto.wallet.RiderCommissionConfigDTO;
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
import com.youdash.util.OutstationPayableLegSplit;

/**
 * Wallet, withdrawals, per-order settlement (including split OUTSTATION legs).
 */
@Service
public class RiderWalletServiceImpl implements RiderWalletService {

    private static final Logger log = LoggerFactory.getLogger(RiderWalletServiceImpl.class);

    private static final long COMMISSION_CONFIG_ID = 1L;

    @Autowired
    private RiderWalletRepository riderWalletRepository;

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
        return orderRiderFinancialRepository.countByOrderId(order.getId()) >= need;
    }

    private int expectedSettlementLegCount(OrderEntity orderEntity) {
        if (orderEntity.getServiceMode() != com.youdash.model.ServiceMode.OUTSTATION) {
            return 1;
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
    @Transactional
    public ApiResponse<RiderWalletSummaryDTO> getWalletSummary(Long riderId) {
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
    @Transactional
    public void settleOrderDelivered(OrderEntity order, CodCollectionMode codMode, Double codCollectedAmount,
            Long actorUserId, String actorType) {
        if (order == null || order.getId() == null) {
            return;
        }
        if (order.getStatus() != OrderStatus.DELIVERED) {
            return;
        }
        int expectedLegs = expectedSettlementLegCount(order);
        if (orderRiderFinancialRepository.countByOrderId(order.getId()) >= expectedLegs) {
            return;
        }
        if (expectedLegs == 1 && order.getRiderId() == null) {
            return;
        }

        ensureDefaultCommissionConfig();
        RiderCommissionConfigEntity cfg = riderCommissionConfigRepository.findById(COMMISSION_CONFIG_ID).orElseThrow();

        double orderAmount = nz(order.getTotalAmount());
        PaymentType payType = order.getPaymentType();

        if (payType == PaymentType.COD && codMode == null) {
            throw new RuntimeException("codCollectionMode is required for COD orders");
        }
        double commissionPercent = resolveCommissionPercent(cfg, payType, codMode);
        double commissionAmount = round2(orderAmount * (commissionPercent / 100.0));
        double baseRiderEarning = round2(orderAmount - commissionAmount);
        double peakBonusTotal = peakIncentiveService.resolveBonusForDeliveredOrder(order, Instant.now());
        double totalRiderPool = round2(baseRiderEarning + peakBonusTotal);
        if (totalRiderPool < -0.0001) {
            throw new RuntimeException("Invalid commission config: rider earning cannot be negative");
        }

        if (expectedLegs == 1) {
            settleSingleRiderOrderDelivered(
                    order,
                    payType,
                    codMode,
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
                codMode,
                orderAmount,
                commissionPercent,
                commissionAmount,
                baseRiderEarning,
                peakBonusTotal,
                actorUserId,
                actorType);
    }

    @Override
    @Transactional(rollbackFor = Exception.class, propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void settlePickupLegAtOriginHub(OrderEntity order, Long pickupRiderId) {
        if (order == null || order.getId() == null || pickupRiderId == null) return;
        if (order.getStatus() != OrderStatus.AT_ORIGIN_HUB) return;
        if (order.getServiceMode() != ServiceMode.OUTSTATION) return;

        // Idempotent: skip if already settled for this rider
        if (orderRiderFinancialRepository.findByOrderIdAndRiderId(order.getId(), pickupRiderId).isPresent()) return;

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
        // Pickup rider never collects COD (cash collected by drop rider at delivery)
        double commissionPercent = resolveCommissionPercent(cfg, PaymentType.ONLINE, null);
        double commPick = round2(oaPick * (commissionPercent / 100.0));
        double basePick = round2(Math.max(0.0, oaPick - commPick));
        double peakBonusTotal = peakIncentiveService.resolveBonusForDeliveredOrder(order, Instant.now());
        double riderLegDen = oaPick + oaDrop;
        double peakPick = riderLegDen > 0.0 ? round2(peakBonusTotal * (oaPick / riderLegDen)) : 0.0;
        double earnPick = round2(Math.max(0.0, basePick + peakPick));

        OrderRiderFinancialEntity finP = new OrderRiderFinancialEntity();
        finP.setOrderId(order.getId());
        finP.setRiderId(pickupRiderId);
        finP.setOrderAmount(oaPick);
        finP.setCommissionPercentApplied(commissionPercent);
        finP.setCommissionAmount(commPick);
        finP.setSurgeBonusAmount(peakPick);
        finP.setRiderEarningAmount(earnPick);
        finP.setCodCollectedAmount(null);
        finP.setCodCollectionMode(null);
        finP.setCodSettlementStatus(CodSettlementStatus.SETTLED);
        finP.setSettledAt(Instant.now());

        try {
            orderRiderFinancialRepository.saveAndFlush(finP);
        } catch (DataIntegrityViolationException ex) {
            log.info("PICKUP_LEG_SETTLE_RACE orderId={} pickupRider={} - row already exists, skipping",
                    order.getId(), pickupRiderId);
            return;
        }

        creditOnlineEarningToWallet(order, pickupRiderId, earnPick, oaPick, commissionPercent, commPick, basePick, peakPick);

        log.info("PICKUP_LEG_SETTLED -> orderId={}, pickupRider={}, earnPick={}", order.getId(), pickupRiderId, earnPick);
        audit("ORDER_SETTLE_PICKUP_LEG", "RIDER", pickupRiderId, "ORDER", order.getId(), Map.of(
                "pickupRiderId", pickupRiderId,
                "pickupEarning", earnPick,
                "paymentType", payType.name()));
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
        Long riderId = order.getRiderId();
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
            collected = round2(orderAmount);
            if (collected <= 0) {
                throw new RuntimeException("Invalid order total for COD settlement");
            }
            fin.setCodCollectedAmount(collected);
            fin.setCodCollectionMode(codMode);
            fin.setCodSettlementStatus(CodSettlementStatus.PENDING);
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
        } else {
            creditCodEarningAndCollectionToWallet(
                    order, riderId, riderEarning, orderAmount, commissionPercent, commissionAmount, baseRiderEarning,
                    peakBonus, codMode, collected);
        }

        audit("ORDER_SETTLE_DELIVERED", actorType, actorUserId, "ORDER", order.getId(), Map.of(
                "riderId", riderId,
                "paymentType", payType.name(),
                "riderEarning", riderEarning));

        sendRiderEarningCreditedNotification(order.getId(), riderId, riderEarning, payType);
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
        Long pickupId = order.getPickupRiderId();
        Long deliveryId = order.getDeliveryRiderId();
        if (pickupId == null || deliveryId == null) {
            throw new RuntimeException("OUTSTATION split settlement requires pickupRiderId and deliveryRiderId");
        }
        if (Objects.equals(pickupId, deliveryId)) {
            throw new RuntimeException("OUTSTATION split settlement requires distinct pickup and delivery riders");
        }

        OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
        double oaPick = leg.pickupAmount();
        double oaDrop = leg.lastMileAmount();
        double commPick = round2(oaPick * (commissionPercent / 100.0));
        double commDrop = round2(oaDrop * (commissionPercent / 100.0));
        double basePick = round2(Math.max(0.0, oaPick - commPick));
        double baseDrop = round2(Math.max(0.0, oaDrop - commDrop));
        double riderLegDen = oaPick + oaDrop;
        double peakPick = riderLegDen > 0.0 ? round2(peakBonusTotal * (oaPick / riderLegDen)) : 0.0;
        double peakDrop = round2(peakBonusTotal - peakPick);
        double earnPick = round2(Math.max(0.0, basePick + peakPick));
        double earnDrop = round2(Math.max(0.0, baseDrop + peakDrop));

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

        try {
            orderRiderFinancialRepository.saveAndFlush(finD);
        } catch (DataIntegrityViolationException ex) {
            log.info("ORDER_SETTLE_RACE orderId={} deliveryRider={} - row already exists, skipping",
                    order.getId(), deliveryId);
            return;
        }

        Long codCollectorRiderId = payType == PaymentType.COD ? resolveCodCollectorRiderId(order) : null;
        boolean pickupCollectsCod = codCollectorRiderId != null && Objects.equals(codCollectorRiderId, pickupId);

        if (!pickupAlreadySettled) {
            if (payType == PaymentType.ONLINE || !pickupCollectsCod) {
                creditOnlineEarningToWallet(order, pickupId, earnPick, oaPick, commissionPercent, commPick, basePick, peakPick);
            } else {
                creditCodEarningAndCollectionToWallet(
                        order, pickupId, earnPick, oaPick, commissionPercent, commPick, basePick, peakPick, codMode, collected);
            }
        }

        if (payType == PaymentType.ONLINE || pickupCollectsCod) {
            creditOnlineEarningToWallet(order, deliveryId, earnDrop, oaDrop, commissionPercent, commDrop, baseDrop, peakDrop);
        } else {
            creditCodEarningAndCollectionToWallet(
                    order, deliveryId, earnDrop, oaDrop, commissionPercent, commDrop, baseDrop, peakDrop, codMode, collected);
        }

        log.info(
                "EARNING_SPLIT -> orderId={}, pickupRider={}, pickupEarning={} (preSettled={}), deliveryRider={}, deliveryEarning={}",
                order.getId(), pickupId, earnPick, pickupAlreadySettled, deliveryId, earnDrop);

        audit("ORDER_SETTLE_DELIVERED_SPLIT", actorType, actorUserId, "ORDER", order.getId(), Map.of(
                "pickupRiderId", pickupId,
                "deliveryRiderId", deliveryId,
                "pickupEarning", earnPick,
                "deliveryEarning", earnDrop,
                "paymentType", payType.name()));

        if (!pickupAlreadySettled) sendRiderEarningCreditedNotification(order.getId(), pickupId, earnPick, payType);
        sendRiderEarningCreditedNotification(order.getId(), deliveryId, earnDrop, payType);
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

    private void creditCodEarningAndCollectionToWallet(
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
        RiderWalletEntity wallet = riderWalletRepository.lockByRiderId(riderId)
                .orElseGet(() -> riderWalletRepository.save(newWallet(riderId)));
        if (wallet.getCurrentBalance() < -0.0001) {
            throw new RuntimeException("Wallet in invalid negative state");
        }
        wallet.setTotalEarnings(round2(wallet.getTotalEarnings() + riderEarning));
        wallet.setCurrentBalance(round2(wallet.getCurrentBalance() + riderEarning));
        wallet.setCodPendingAmount(round2(wallet.getCodPendingAmount() + collected));
        riderWalletRepository.save(wallet);

        log.info(
                "COD_FLOW -> orderId={}, riderId={}, codCollectedAmount={}, codPending={}, riderBalance={}",
                order.getId(),
                riderId,
                collected,
                wallet.getCodPendingAmount(),
                wallet.getCurrentBalance());

        RiderWalletTransactionEntity earnTxn = new RiderWalletTransactionEntity();
        earnTxn.setRiderId(riderId);
        earnTxn.setType(WalletTxnType.CREDIT);
        earnTxn.setAmount(riderEarning);
        earnTxn.setReferenceType(WalletTxnReferenceType.ORDER);
        earnTxn.setReferenceId(order.getId());
        earnTxn.setStatus(WalletTxnStatus.COMPLETED);
        earnTxn.setNote("Order delivered - rider earning credit (COD)");
        earnTxn.setMetadataJson(writeJson(buildEarningTxnMetadata(
                orderAmountPortion,
                PaymentType.COD,
                commissionPercent,
                commissionAmountPortion,
                basePortion,
                peakPortion,
                riderEarning,
                codMode,
                collected)));
        riderWalletTransactionRepository.save(earnTxn);

        RiderWalletTransactionEntity codTxn = new RiderWalletTransactionEntity();
        codTxn.setRiderId(riderId);
        codTxn.setType(WalletTxnType.CREDIT);
        codTxn.setAmount(collected);
        codTxn.setReferenceType(WalletTxnReferenceType.ORDER);
        codTxn.setReferenceId(order.getId());
        codTxn.setStatus(WalletTxnStatus.COMPLETED);
        codTxn.setNote("COD collected by rider - pending settlement");
        Map<String, Object> codMeta = new HashMap<>();
        codMeta.put("codCollectionMode", codMode.name());
        codMeta.put("codCollectedAmount", collected);
        codTxn.setMetadataJson(writeJson(codMeta));
        riderWalletTransactionRepository.save(codTxn);
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
                // Both legs assigned to different riders: report combined for admin/summary use
                return round2(pickupNet + dropNet);
            }
            // Pre-assignment or single rider: show pickup leg earning as the dispatch estimate
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
        if (order == null) {
            return null;
        }
        if (order.getServiceMode() == com.youdash.model.ServiceMode.OUTSTATION) {
            String deliveryType = nzStr(order.getDeliveryType()).trim().toUpperCase();
            if ("DOOR_TO_DOOR".equals(deliveryType) || "HUB_TO_DOOR".equals(deliveryType)) {
                return order.getPickupRiderId() != null ? order.getPickupRiderId() : order.getRiderId();
            }
            return order.getDeliveryRiderId() != null ? order.getDeliveryRiderId() : order.getRiderId();
        }
        return order.getRiderId();
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

            double expected = nz(fin.getCodCollectedAmount());
            double amt = round2(dto.getAmount());
            if (expected <= 0) {
                throw new RuntimeException("No COD amount recorded for this order");
            }
            if (Math.abs(expected - amt) > 0.01) {
                throw new RuntimeException("amount must match collected COD for this order");
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
            if (wallet.getCurrentBalance() < -0.0001) {
                throw new RuntimeException("Wallet in invalid negative state");
            }
            if (wallet.getCodPendingAmount() + 0.0001 < amt) {
                throw new RuntimeException("COD pending mismatch");
            }

            wallet.setCurrentBalance(round2(wallet.getCurrentBalance() - amt));
            wallet.setCodPendingAmount(round2(wallet.getCodPendingAmount() - amt));
            riderWalletRepository.save(wallet);

            RiderWalletTransactionEntity txn = new RiderWalletTransactionEntity();
            txn.setRiderId(codRiderId);
            txn.setType(WalletTxnType.DEBIT);
            txn.setAmount(amt);
            txn.setReferenceType(WalletTxnReferenceType.ORDER);
            txn.setReferenceId(order.getId());
            txn.setStatus(WalletTxnStatus.COMPLETED);
            txn.setNote("COD settled to admin");
            Map<String, Object> settleMeta = new HashMap<>();
            settleMeta.put("orderId", order.getId());
            txn.setMetadataJson(writeJson(settleMeta));
            riderWalletTransactionRepository.save(txn);

            fin.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            fin.setSettledAt(Instant.now());
            orderRiderFinancialRepository.save(fin);

            order.setCodSettlementStatus(CodSettlementStatus.SETTLED);
            orderRepository.save(order);

            Map<String, Object> codAudit = new HashMap<>();
            codAudit.put("riderId", codRiderId);
            codAudit.put("amount", amt);
            audit("COD_SETTLE", "ADMIN", adminUserId, "ORDER", order.getId(), codAudit);

            if (notificationDedupService.tryAcquire("rider-cod-settled:" + order.getId())) {
                Map<String, String> codData = new HashMap<>(
                        NotificationService.baseData(
                                order.getId(),
                                order.getStatus() != null ? order.getStatus().name() : null,
                                NotificationType.RIDER_COD_SETTLED_ADMIN));
                codData.put("settledAmount", String.valueOf(amt));
                String codBody = "Rs. " + String.format("%.2f", amt) + " COD for order " + order.getId()
                        + " was settled.";
                notificationService.sendToRider(
                        codRiderId,
                        "COD settled",
                        codBody,
                        codData,
                        NotificationType.RIDER_COD_SETTLED_ADMIN);
            }

            response.setData("OK");
            response.setMessage("COD settled");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
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

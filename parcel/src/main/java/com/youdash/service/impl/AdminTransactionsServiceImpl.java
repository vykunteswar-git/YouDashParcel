package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminTransactionItemDTO;
import com.youdash.dto.admin.AdminTransactionSummaryDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.UserEntity;
import com.youdash.entity.wallet.RiderWithdrawalEntity;
import com.youdash.model.PaymentType;
import com.youdash.model.wallet.WithdrawalStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.repository.wallet.RiderWithdrawalRepository;
import com.youdash.service.AdminTransactionsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminTransactionsServiceImpl implements AdminTransactionsService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderWithdrawalRepository riderWithdrawalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<AdminTransactionSummaryDTO> getSummary(String from, String to) {
        ApiResponse<AdminTransactionSummaryDTO> response = new ApiResponse<>();
        try {
            TimeWindow w = resolveWindow(from, to);
            List<OrderEntity> paidOrders = orderRepository
                    .findByPaymentStatusIgnoreCaseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                            "PAID", w.from, w.to);
            List<OrderEntity> gatewayOrders = orderRepository
                    .findByPaymentStatusIgnoreCaseAndPaymentTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                            "PAID", PaymentType.ONLINE, w.from, w.to);
            List<RiderWithdrawalEntity> payouts = riderWithdrawalRepository
                    .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(w.from, w.to);
            List<RiderWithdrawalEntity> activePayouts = riderWithdrawalRepository
                    .findByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                            WithdrawalStatus.PENDING, w.from, w.to);

            double ordersVol = paidOrders.stream().mapToDouble(o -> nz(o.getTotalAmount())).sum();
            double payoutsVol = payouts.stream().mapToDouble(p -> nz(p.getAmount())).sum();
            double gatewayVol = gatewayOrders.stream().mapToDouble(o -> nz(o.getTotalAmount())).sum();
            double activePayoutAmount = activePayouts.stream().mapToDouble(p -> nz(p.getAmount())).sum();

            AdminTransactionSummaryDTO data = AdminTransactionSummaryDTO.builder()
                    .from(w.from.toString())
                    .to(w.to.toString())
                    .totalVolume(round2(ordersVol + payoutsVol))
                    .activePayoutAmount(round2(activePayoutAmount))
                    .paymentGatewayVolume(round2(gatewayVol))
                    .totalTransactions((long) (paidOrders.size() + payouts.size()))
                    .build();

            response.setData(data);
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
    public ApiResponse<List<AdminTransactionItemDTO>> listTransactions(
            String type,
            String status,
            String q,
            String from,
            String to,
            int page,
            int size) {
        ApiResponse<List<AdminTransactionItemDTO>> response = new ApiResponse<>();
        try {
            TimeWindow w = resolveWindow(from, to);
            String normalizedType = normalize(type);
            String normalizedStatus = normalize(status);
            String query = q == null ? null : q.trim().toLowerCase(Locale.ROOT);

            List<AdminTransactionItemDTO> merged = new ArrayList<>();
            boolean includeOrders = normalizedType == null || "ALL".equals(normalizedType) || "ORDER_PAY".equals(normalizedType);
            boolean includePayouts = normalizedType == null || "ALL".equals(normalizedType) || "PAYOUT".equals(normalizedType);

            if (includeOrders) {
                List<OrderEntity> paidOrders = orderRepository
                        .findByPaymentStatusIgnoreCaseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                                "PAID", w.from, w.to);
                Map<Long, UserEntity> users = userRepository.findAllById(
                        paidOrders.stream().map(OrderEntity::getUserId).filter(Objects::nonNull).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(UserEntity::getId, u -> u));
                for (OrderEntity o : paidOrders) {
                    String orderStatus = o.getPaymentStatus() == null ? "UNKNOWN" : o.getPaymentStatus().toUpperCase(Locale.ROOT);
                    if (normalizedStatus != null && !orderStatus.equals(normalizedStatus)) {
                        continue;
                    }
                    UserEntity u = users.get(o.getUserId());
                    String partyName = userDisplayName(u, o.getUserId());
                    AdminTransactionItemDTO item = AdminTransactionItemDTO.builder()
                            .txnId("ORD-" + o.getId())
                            .sourceType("ORDER_PAY")
                            .sourceId(o.getId())
                            .partyType("USER")
                            .partyId(o.getUserId())
                            .partyName(partyName)
                            .method(o.getPaymentType() == null ? null : o.getPaymentType().name())
                            .status(orderStatus)
                            .amount(round2(nz(o.getTotalAmount())))
                            .createdAt(o.getCreatedAt() == null ? null : o.getCreatedAt().toString())
                            .reference(o.getDisplayOrderId() != null ? o.getDisplayOrderId() : ("#" + o.getId()))
                            .build();
                    if (matchesQuery(item, query)) {
                        merged.add(item);
                    }
                }
            }

            if (includePayouts) {
                List<RiderWithdrawalEntity> payouts = riderWithdrawalRepository
                        .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(w.from, w.to);
                Map<Long, RiderEntity> riders = riderRepository.findAllById(
                        payouts.stream().map(RiderWithdrawalEntity::getRiderId).filter(Objects::nonNull).collect(Collectors.toSet()))
                        .stream().collect(Collectors.toMap(RiderEntity::getId, r -> r));
                for (RiderWithdrawalEntity p : payouts) {
                    String payoutStatus = p.getStatus() == null ? "UNKNOWN" : p.getStatus().name();
                    if (normalizedStatus != null && !payoutStatus.equals(normalizedStatus)) {
                        continue;
                    }
                    RiderEntity r = riders.get(p.getRiderId());
                    String partyName = r != null && r.getName() != null && !r.getName().isBlank()
                            ? r.getName().trim()
                            : "Rider #" + p.getRiderId();
                    AdminTransactionItemDTO item = AdminTransactionItemDTO.builder()
                            .txnId("PAY-" + p.getId())
                            .sourceType("PAYOUT")
                            .sourceId(p.getId())
                            .partyType("RIDER")
                            .partyId(p.getRiderId())
                            .partyName(partyName)
                            .method("BANK_TRANSFER")
                            .status(payoutStatus)
                            .amount(round2(nz(p.getAmount())))
                            .createdAt(p.getCreatedAt() == null ? null : p.getCreatedAt().toString())
                            .reference("WITHDRAW-" + p.getId())
                            .build();
                    if (matchesQuery(item, query)) {
                        merged.add(item);
                    }
                }
            }

            merged.sort(Comparator.comparing(AdminTransactionItemDTO::getCreatedAt, Comparator.nullsLast(String::compareTo)).reversed());
            int safeSize = Math.min(Math.max(size, 1), 200);
            int safePage = Math.max(page, 0);
            int fromIdx = Math.min(safePage * safeSize, merged.size());
            int toIdx = Math.min(fromIdx + safeSize, merged.size());
            List<AdminTransactionItemDTO> pageList = merged.subList(fromIdx, toIdx);

            response.setData(pageList);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(merged.size());
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    private static boolean matchesQuery(AdminTransactionItemDTO item, String q) {
        if (q == null || q.isBlank()) {
            return true;
        }
        return contains(item.getTxnId(), q)
                || contains(item.getPartyName(), q)
                || contains(item.getReference(), q)
                || contains(item.getStatus(), q)
                || contains(item.getMethod(), q);
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private static String userDisplayName(UserEntity u, Long userId) {
        if (u == null) {
            return "User #" + userId;
        }
        String fn = u.getFirstName() == null ? "" : u.getFirstName().trim();
        String ln = u.getLastName() == null ? "" : u.getLastName().trim();
        String name = (fn + " " + ln).trim();
        if (!name.isEmpty()) {
            return name;
        }
        if (u.getPhoneNumber() != null && !u.getPhoneNumber().isBlank()) {
            return u.getPhoneNumber().trim();
        }
        return "User #" + u.getId();
    }

    private static TimeWindow resolveWindow(String from, String to) {
        Instant now = Instant.now();
        Instant start = (from == null || from.isBlank()) ? now.minus(30, ChronoUnit.DAYS) : Instant.parse(from.trim());
        Instant end = (to == null || to.isBlank()) ? now : Instant.parse(to.trim());
        if (!end.isAfter(start)) {
            throw new RuntimeException("to must be after from");
        }
        return new TimeWindow(start, end);
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static void setErr(ApiResponse<?> r, String m) {
        r.setMessage(m);
        r.setMessageKey("ERROR");
        r.setSuccess(false);
        r.setStatus(500);
    }

    private record TimeWindow(Instant from, Instant to) {}
}

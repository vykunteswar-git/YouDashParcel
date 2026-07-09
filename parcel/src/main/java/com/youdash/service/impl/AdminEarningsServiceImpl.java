package com.youdash.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminEarningsDTO;
import com.youdash.dto.admin.AdminEarningsOrderRowDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.wallet.OrderRiderFinancialEntity;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.wallet.OrderRiderFinancialRepository;
import com.youdash.service.AdminEarningsService;

@Service
public class AdminEarningsServiceImpl implements AdminEarningsService {

    private static final ZoneId REPORTING_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ISO_FMT = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderRiderFinancialRepository orderRiderFinancialRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<AdminEarningsDTO> getEarnings(String range, String from, String to) {
        ApiResponse<AdminEarningsDTO> response = new ApiResponse<>();
        try {
            Instant[] window = resolveWindow(range, from, to);
            Instant fromInstant = window[0];
            Instant toInstant = window[1];

            List<OrderEntity> orders = orderRepository
                    .findEarningsOrdersInRange(fromInstant, toInstant, PageRequest.of(0, 1000));

            List<Long> orderIds = orders.stream().map(OrderEntity::getId).toList();

            Map<Long, List<OrderRiderFinancialEntity>> finsByOrder =
                    orderIds.isEmpty()
                            ? Map.of()
                            : orderRiderFinancialRepository.findByOrderIdIn(orderIds)
                                    .stream()
                                    .collect(Collectors.groupingBy(OrderRiderFinancialEntity::getOrderId));

            List<AdminEarningsOrderRowDTO> rows = orders.stream()
                    .sorted(Comparator.comparing(OrderEntity::getCreatedAt).reversed())
                    .map(o -> toRow(o, finsByOrder.getOrDefault(o.getId(), List.of())))
                    .toList();

            double totalRevenue     = orders.stream().mapToDouble(o -> nz(o.getTotalAmount())).sum();
            double totalGst         = orders.stream().mapToDouble(o -> nz(o.getGstAmount())).sum();
            double totalPlatformFee = orders.stream().mapToDouble(o -> nz(o.getPlatformFee())).sum();

            List<OrderRiderFinancialEntity> allFins = finsByOrder.values().stream()
                    .flatMap(List::stream).toList();
            double totalCommission   = allFins.stream().mapToDouble(f -> nz(f.getCommissionAmount())).sum();
            double totalRiderPayouts = allFins.stream().mapToDouble(f -> nz(f.getRiderEarningAmount())).sum();

            AdminEarningsDTO dto = new AdminEarningsDTO();
            dto.setRange(range);
            dto.setOrderCount(orders.size());
            dto.setTotalRevenue(round2(totalRevenue));
            dto.setTotalCommission(round2(totalCommission));
            dto.setTotalGst(round2(totalGst));
            dto.setTotalPlatformFee(round2(totalPlatformFee));
            dto.setTotalPlatformNet(round2(totalCommission + totalGst + totalPlatformFee));
            dto.setTotalRiderPayouts(round2(totalRiderPayouts));
            dto.setOrders(rows);

            response.setData(dto);
            response.setSuccess(true);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
        } catch (Exception e) {
            response.setSuccess(false);
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
        }
        return response;
    }

    private AdminEarningsOrderRowDTO toRow(OrderEntity o, List<OrderRiderFinancialEntity> fins) {
        double totalAmount  = nz(o.getTotalAmount());
        double subtotal     = o.getSubtotal() != null && o.getSubtotal() > 0 ? o.getSubtotal() : totalAmount;
        double gst          = nz(o.getGstAmount());
        double platformFee  = nz(o.getPlatformFee());

        double commission   = fins.stream().mapToDouble(f -> nz(f.getCommissionAmount())).sum();
        double riderEarning = fins.stream().mapToDouble(f -> nz(f.getRiderEarningAmount())).sum();
        double commPct      = fins.stream().mapToDouble(f -> nz(f.getCommissionPercentApplied())).average().orElse(0.0);

        AdminEarningsOrderRowDTO row = new AdminEarningsOrderRowDTO();
        row.setOrderId(o.getId());
        row.setDisplayOrderId(o.getDisplayOrderId() != null ? o.getDisplayOrderId() : "YD-" + o.getId());
        row.setCreatedAt(o.getCreatedAt() != null
                ? ISO_FMT.format(o.getCreatedAt().atZone(REPORTING_ZONE)) : null);
        row.setServiceMode(o.getServiceMode() != null ? o.getServiceMode().name() : null);
        row.setPaymentType(o.getPaymentType() != null ? o.getPaymentType().name() : null);
        row.setPaymentStatus(o.getPaymentStatus());
        row.setTotalAmount(round2(totalAmount));
        row.setSubtotal(round2(subtotal));
        row.setGstAmount(round2(gst));
        row.setPlatformFee(round2(platformFee));
        row.setCommissionPercent(round2(commPct));
        row.setCommissionAmount(round2(commission));
        row.setRiderEarning(round2(riderEarning));
        row.setPlatformNet(round2(commission + gst + platformFee));
        return row;
    }

    private static Instant[] resolveWindow(String range, String fromDate, String toDate) {
        ZonedDateTime now = ZonedDateTime.now(REPORTING_ZONE);
        if (fromDate != null && !fromDate.isBlank()) {
            ZonedDateTime from = LocalDate.parse(fromDate.trim()).atStartOfDay(REPORTING_ZONE);
            ZonedDateTime to = (toDate != null && !toDate.isBlank())
                    ? LocalDate.parse(toDate.trim()).plusDays(1).atStartOfDay(REPORTING_ZONE)
                    : now;
            return new Instant[]{from.toInstant(), to.toInstant()};
        }
        ZonedDateTime from;
        switch (range == null ? "" : range.toUpperCase()) {
            case "TODAY":
                from = now.toLocalDate().atStartOfDay(REPORTING_ZONE);
                break;
            case "THIS_MONTH":
                from = now.with(TemporalAdjusters.firstDayOfMonth()).toLocalDate().atStartOfDay(REPORTING_ZONE);
                break;
            default: // THIS_WEEK
                from = now.toLocalDate().minusDays(6).atStartOfDay(REPORTING_ZONE);
        }
        return new Instant[]{from.toInstant(), now.toInstant()};
    }

    private static double nz(Double v) { return v != null ? v : 0.0; }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}

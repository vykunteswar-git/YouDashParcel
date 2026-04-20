package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminDashboardActivityDTO;
import com.youdash.dto.admin.AdminDashboardSummaryDTO;
import com.youdash.dto.admin.AdminDashboardTrendPointDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private enum DashboardRange {
        TODAY, THIS_WEEK, THIS_MONTH
    }

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<AdminDashboardSummaryDTO> getSummary(String range) {
        ApiResponse<AdminDashboardSummaryDTO> response = new ApiResponse<>();
        try {
            DashboardRange rr = parseRange(range);
            TimeRange tr = resolveTimeRange(rr);
            List<OrderEntity> orders = orderRepository
                    .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(tr.from(), tr.to());

            long totalOrders = orders.size();
            long deliveredOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            long cancelledOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.CANCELLED).count();
            double grossRevenue = orders.stream()
                    .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                    .mapToDouble(o -> nz(o.getTotalAmount()))
                    .sum();

            long onlineRiders = riderRepository.countByApprovalStatusAndIsAvailableTrue(RiderApprovalStatus.APPROVED);
            Set<Long> uniqueUsers = orders.stream()
                    .map(OrderEntity::getUserId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            long activeUsers = uniqueUsers.size();
            if (activeUsers == 0) {
                activeUsers = userRepository.countByActiveTrue();
            }

            AdminDashboardSummaryDTO dto = new AdminDashboardSummaryDTO();
            dto.setRange(rr.name());
            dto.setFrom(tr.from().toString());
            dto.setTo(tr.to().toString());
            dto.setTotalOrders(totalOrders);
            dto.setGrossRevenue(round2(grossRevenue));
            dto.setOnlineRiders(onlineRiders);
            dto.setActiveUsers(activeUsers);
            dto.setAvgAssignmentEtaMinutes(round2(avgAssignmentEtaMinutes(orders)));
            dto.setCancellationRate(round2(percentage(cancelledOrders, totalOrders)));
            dto.setCompletionRate(round2(percentage(deliveredOrders, totalOrders)));
            dto.setAvgOrderValue(round2(totalOrders == 0 ? 0.0 : grossRevenue / totalOrders));

            response.setData(dto);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminDashboardTrendPointDTO>> getOrderVolumeTrend(String range) {
        ApiResponse<List<AdminDashboardTrendPointDTO>> response = new ApiResponse<>();
        try {
            DashboardRange rr = parseRange(range);
            TimeRange tr = resolveTimeRange(rr);
            List<OrderEntity> orders = orderRepository
                    .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(tr.from(), tr.to());
            List<AdminDashboardTrendPointDTO> trend = buildOrderTrend(rr, tr.zone(), tr.today(), orders);

            response.setData(trend);
            response.setTotalCount(trend.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminDashboardActivityDTO>> getLiveActivity(int limit) {
        ApiResponse<List<AdminDashboardActivityDTO>> response = new ApiResponse<>();
        try {
            int pageSize = Math.min(Math.max(limit, 1), 100);
            var pageable = PageRequest.of(0, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<OrderEntity> orders = orderRepository.findAll(pageable).getContent();

            Set<Long> riderIds = orders.stream()
                    .map(OrderEntity::getRiderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, String> riderNames = new HashMap<>();
            if (!riderIds.isEmpty()) {
                List<RiderEntity> riders = riderRepository.findAllById(riderIds);
                for (RiderEntity r : riders) {
                    riderNames.put(r.getId(), r.getName());
                }
            }

            List<AdminDashboardActivityDTO> rows = new ArrayList<>();
            for (OrderEntity o : orders) {
                AdminDashboardActivityDTO row = new AdminDashboardActivityDTO();
                row.setOrderId(o.getId());
                row.setDisplayOrderId(o.getDisplayOrderId());
                row.setCustomerName(firstNonBlank(o.getSenderName(), o.getReceiverName(), "Customer"));
                row.setRiderName(o.getRiderId() == null ? null : riderNames.get(o.getRiderId()));
                row.setServiceMode(o.getServiceMode() != null ? o.getServiceMode().name() : null);
                row.setStatus(o.getStatus() != null ? o.getStatus().name() : null);
                row.setAmount(round2(nz(o.getTotalAmount())));
                row.setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null);
                rows.add(row);
            }

            response.setData(rows);
            response.setTotalCount(rows.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    private static DashboardRange parseRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return DashboardRange.TODAY;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (v) {
            case "TODAY" -> DashboardRange.TODAY;
            case "THIS_WEEK", "WEEK" -> DashboardRange.THIS_WEEK;
            case "THIS_MONTH", "MONTH" -> DashboardRange.THIS_MONTH;
            default -> throw new RuntimeException("Invalid range. Use TODAY, THIS_WEEK, or THIS_MONTH");
        };
    }

    private static TimeRange resolveTimeRange(DashboardRange rr) {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Instant to = now.toInstant();
        Instant from = switch (rr) {
            case TODAY -> now.toLocalDate().atStartOfDay(zone).toInstant();
            case THIS_WEEK -> now.with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay(zone).toInstant();
            case THIS_MONTH -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone).toInstant();
        };
        return new TimeRange(from, to, zone, now.toLocalDate());
    }

    private static List<AdminDashboardTrendPointDTO> buildOrderTrend(
            DashboardRange rr,
            ZoneId zone,
            LocalDate today,
            List<OrderEntity> orders) {
        if (rr == DashboardRange.TODAY) {
            return buildTodayTrend(zone, orders);
        }
        if (rr == DashboardRange.THIS_WEEK) {
            return buildWeekTrend(zone, today, orders);
        }
        return buildMonthTrend(zone, today, orders);
    }

    private static List<AdminDashboardTrendPointDTO> buildTodayTrend(ZoneId zone, List<OrderEntity> orders) {
        LinkedHashMap<String, Long> buckets = new LinkedHashMap<>();
        for (int h = 0; h < 24; h += 3) {
            buckets.put(String.format("%02d:00", h), 0L);
        }
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            int hour = LocalDateTime.ofInstant(o.getCreatedAt(), zone).getHour();
            String label = String.format("%02d:00", (hour / 3) * 3);
            if (buckets.containsKey(label)) {
                buckets.put(label, buckets.get(label) + 1L);
            }
        }
        return toTrendList(buckets);
    }

    private static List<AdminDashboardTrendPointDTO> buildWeekTrend(ZoneId zone, LocalDate today, List<OrderEntity> orders) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LinkedHashMap<String, Long> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            String label = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            buckets.put(label, 0L);
        }
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            String label = LocalDateTime.ofInstant(o.getCreatedAt(), zone)
                    .getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            if (buckets.containsKey(label)) {
                buckets.put(label, buckets.get(label) + 1L);
            }
        }
        return toTrendList(buckets);
    }

    private static List<AdminDashboardTrendPointDTO> buildMonthTrend(ZoneId zone, LocalDate today, List<OrderEntity> orders) {
        int days = today.lengthOfMonth();
        LinkedHashMap<String, Long> buckets = new LinkedHashMap<>();
        for (int i = 1; i <= days; i++) {
            buckets.put(String.valueOf(i), 0L);
        }
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            int day = LocalDateTime.ofInstant(o.getCreatedAt(), zone).getDayOfMonth();
            String label = String.valueOf(day);
            if (buckets.containsKey(label)) {
                buckets.put(label, buckets.get(label) + 1L);
            }
        }
        return toTrendList(buckets);
    }

    private static List<AdminDashboardTrendPointDTO> toTrendList(LinkedHashMap<String, Long> buckets) {
        List<AdminDashboardTrendPointDTO> out = new ArrayList<>();
        buckets.forEach((k, v) -> out.add(new AdminDashboardTrendPointDTO(k, v)));
        return out;
    }

    private static double avgAssignmentEtaMinutes(List<OrderEntity> orders) {
        long count = 0;
        double sum = 0.0;
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null || o.getAcceptedAt() == null || o.getAcceptedAt().isBefore(o.getCreatedAt())) {
                continue;
            }
            sum += Duration.between(o.getCreatedAt(), o.getAcceptedAt()).toSeconds() / 60.0;
            count++;
        }
        return count == 0 ? 0.0 : sum / count;
    }

    private static double percentage(long num, long den) {
        if (den <= 0) {
            return 0.0;
        }
        return (num * 100.0) / den;
    }

    private static String firstNonBlank(String a, String b, String fallback) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return fallback;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private record TimeRange(Instant from, Instant to, ZoneId zone, LocalDate today) {
    }
}


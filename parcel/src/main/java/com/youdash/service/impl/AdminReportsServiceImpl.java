package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminRevenueReportDTO;
import com.youdash.dto.admin.AdminRevenueTopSourceDTO;
import com.youdash.dto.admin.AdminRevenueTrendPointDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.service.AdminReportsService;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminReportsServiceImpl implements AdminReportsService {

    private enum ReportRange {
        TODAY, THIS_WEEK, THIS_MONTH
    }

    @Autowired
    private OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<AdminRevenueReportDTO> getRevenueReport(String range) {
        ApiResponse<AdminRevenueReportDTO> response = new ApiResponse<>();
        try {
            ReportRange rr = parseRange(range);
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime now = ZonedDateTime.now(zone);
            Instant to = now.toInstant();
            Instant from = switch (rr) {
                case TODAY -> now.toLocalDate().atStartOfDay(zone).toInstant();
                case THIS_WEEK -> now.with(DayOfWeek.MONDAY).toLocalDate().atStartOfDay(zone).toInstant();
                case THIS_MONTH -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone).toInstant();
            };

            List<OrderEntity> orders = orderRepository
                    .findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(from, to);

            AdminRevenueReportDTO dto = new AdminRevenueReportDTO();
            dto.setRange(rr.name());
            dto.setFrom(from.toString());
            dto.setTo(to.toString());

            long totalOrders = orders.size();
            long deliveredOrders = orders.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED).count();
            double totalRevenue = orders.stream()
                    .filter(o -> o.getStatus() == OrderStatus.DELIVERED)
                    .mapToDouble(o -> nz(o.getTotalAmount()))
                    .sum();
            double completionRate = totalOrders == 0 ? 0.0 : (deliveredOrders * 100.0) / totalOrders;

            dto.setTotalRevenue(round2(totalRevenue));
            dto.setCompletionRate(round2(completionRate));
            dto.setAvgAssignmentEtaMinutes(round2(avgAssignmentEtaMinutes(orders)));

            List<AdminRevenueTrendPointDTO> trend = buildTrend(rr, orders, zone, now.toLocalDate());
            dto.setTrend(trend);
            dto.setRushMultiplier(round2(computeRushMultiplier(trend)));
            dto.setTopSources(buildTopSources(orders));

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

    private static ReportRange parseRange(String raw) {
        if (raw == null || raw.isBlank()) {
            return ReportRange.THIS_WEEK;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return switch (v) {
            case "TODAY" -> ReportRange.TODAY;
            case "THIS_WEEK", "WEEK" -> ReportRange.THIS_WEEK;
            case "THIS_MONTH", "MONTH" -> ReportRange.THIS_MONTH;
            default -> throw new RuntimeException("Invalid range. Use TODAY, THIS_WEEK, or THIS_MONTH");
        };
    }

    private static double avgAssignmentEtaMinutes(List<OrderEntity> orders) {
        long count = 0;
        double totalMinutes = 0.0;
        for (OrderEntity o : orders) {
            Instant created = o.getCreatedAt();
            Instant accepted = o.getAcceptedAt();
            if (created == null || accepted == null || accepted.isBefore(created)) {
                continue;
            }
            totalMinutes += Duration.between(created, accepted).toSeconds() / 60.0;
            count++;
        }
        return count == 0 ? 0.0 : (totalMinutes / count);
    }

    private static List<AdminRevenueTrendPointDTO> buildTrend(
            ReportRange rr,
            List<OrderEntity> orders,
            ZoneId zone,
            LocalDate today) {
        if (rr == ReportRange.TODAY) {
            return buildTodayTrend(orders, zone);
        }
        if (rr == ReportRange.THIS_WEEK) {
            return buildWeekTrend(orders, zone, today);
        }
        return buildMonthTrend(orders, zone, today);
    }

    private static List<AdminRevenueTrendPointDTO> buildTodayTrend(List<OrderEntity> orders, ZoneId zone) {
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        for (int h = 0; h < 24; h += 3) {
            String label = String.format("%02d:00", h);
            buckets.put(label, new Bucket());
        }
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            LocalDateTime ldt = LocalDateTime.ofInstant(o.getCreatedAt(), zone);
            int bucketStart = (ldt.getHour() / 3) * 3;
            String key = String.format("%02d:00", bucketStart);
            Bucket b = buckets.get(key);
            if (b == null) {
                continue;
            }
            b.orders++;
            if (o.getStatus() == OrderStatus.DELIVERED) {
                b.revenue += nz(o.getTotalAmount());
            }
        }
        return toTrend(buckets);
    }

    private static List<AdminRevenueTrendPointDTO> buildWeekTrend(List<OrderEntity> orders, ZoneId zone, LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate d = monday.plusDays(i);
            String label = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            buckets.put(label, new Bucket());
        }
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            LocalDate d = LocalDateTime.ofInstant(o.getCreatedAt(), zone).toLocalDate();
            String label = d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            Bucket b = buckets.get(label);
            if (b == null) {
                continue;
            }
            b.orders++;
            if (o.getStatus() == OrderStatus.DELIVERED) {
                b.revenue += nz(o.getTotalAmount());
            }
        }
        return toTrend(buckets);
    }

    private static List<AdminRevenueTrendPointDTO> buildMonthTrend(List<OrderEntity> orders, ZoneId zone, LocalDate today) {
        LocalDate start = today.withDayOfMonth(1);
        int days = today.lengthOfMonth();
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        for (int i = 1; i <= days; i++) {
            buckets.put(String.valueOf(i), new Bucket());
        }
        for (OrderEntity o : orders) {
            if (o.getCreatedAt() == null) {
                continue;
            }
            LocalDate d = LocalDateTime.ofInstant(o.getCreatedAt(), zone).toLocalDate();
            if (d.isBefore(start) || d.getMonthValue() != start.getMonthValue()) {
                continue;
            }
            String label = String.valueOf(d.getDayOfMonth());
            Bucket b = buckets.get(label);
            if (b == null) {
                continue;
            }
            b.orders++;
            if (o.getStatus() == OrderStatus.DELIVERED) {
                b.revenue += nz(o.getTotalAmount());
            }
        }
        return toTrend(buckets);
    }

    private static List<AdminRevenueTrendPointDTO> toTrend(LinkedHashMap<String, Bucket> buckets) {
        List<AdminRevenueTrendPointDTO> out = new ArrayList<>();
        buckets.forEach((label, b) -> out.add(new AdminRevenueTrendPointDTO(label, round2(b.revenue), b.orders)));
        return out;
    }

    private static double computeRushMultiplier(List<AdminRevenueTrendPointDTO> trend) {
        if (trend == null || trend.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        double peak = 0.0;
        int n = 0;
        for (AdminRevenueTrendPointDTO p : trend) {
            double v = nz(p.getRevenue());
            sum += v;
            peak = Math.max(peak, v);
            n++;
        }
        if (n == 0) {
            return 0.0;
        }
        double avg = sum / n;
        if (avg <= 0.0001) {
            return 0.0;
        }
        return peak / avg;
    }

    private static List<AdminRevenueTopSourceDTO> buildTopSources(List<OrderEntity> orders) {
        Map<String, Bucket> bySource = new HashMap<>();
        for (OrderEntity o : orders) {
            String source = resolveSource(o);
            Bucket b = bySource.computeIfAbsent(source, k -> new Bucket());
            b.orders++;
            if (o.getStatus() == OrderStatus.DELIVERED) {
                b.delivered++;
            }
        }

        List<Map.Entry<String, Bucket>> sorted = bySource.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Bucket>>comparingLong(e -> e.getValue().orders).reversed())
                .limit(5)
                .toList();

        List<AdminRevenueTopSourceDTO> out = new ArrayList<>();
        int idx = 1;
        for (Map.Entry<String, Bucket> e : sorted) {
            Bucket b = e.getValue();
            double conversion = b.orders == 0 ? 0.0 : (b.delivered * 100.0) / b.orders;
            out.add(new AdminRevenueTopSourceDTO(
                    "MET-" + (4500 + idx),
                    e.getKey(),
                    b.orders,
                    round2(conversion),
                    "Synced"
            ));
            idx++;
        }
        return out;
    }

    private static String resolveSource(OrderEntity o) {
        if (o == null) {
            return "Unknown";
        }
        if (o.getDeliveryType() != null && !o.getDeliveryType().isBlank()) {
            return o.getDeliveryType();
        }
        if (o.getServiceMode() != null) {
            return o.getServiceMode().name();
        }
        return "Unknown";
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static class Bucket {
        long orders = 0;
        long delivered = 0;
        double revenue = 0.0;
    }
}


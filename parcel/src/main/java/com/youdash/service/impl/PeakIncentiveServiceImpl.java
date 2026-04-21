package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.incentive.PeakIncentiveCampaignDTO;
import com.youdash.dto.incentive.RiderIncentiveProgressDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.PeakIncentiveCampaignEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.PeakIncentiveCampaignRepository;
import com.youdash.service.PeakIncentiveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PeakIncentiveServiceImpl implements PeakIncentiveService {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    @Autowired
    private PeakIncentiveCampaignRepository peakIncentiveCampaignRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PeakIncentiveCampaignDTO>> adminList() {
        ApiResponse<List<PeakIncentiveCampaignDTO>> response = new ApiResponse<>();
        try {
            List<PeakIncentiveCampaignDTO> list = peakIncentiveCampaignRepository.findAll().stream()
                    .sorted(Comparator.comparing(PeakIncentiveCampaignEntity::getId).reversed())
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional
    public ApiResponse<PeakIncentiveCampaignDTO> adminCreate(PeakIncentiveCampaignDTO dto) {
        ApiResponse<PeakIncentiveCampaignDTO> response = new ApiResponse<>();
        try {
            PeakIncentiveCampaignEntity e = new PeakIncentiveCampaignEntity();
            applyDto(e, dto, true);
            PeakIncentiveCampaignEntity saved = peakIncentiveCampaignRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Campaign created");
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
    public ApiResponse<PeakIncentiveCampaignDTO> adminUpdate(Long id, PeakIncentiveCampaignDTO dto) {
        ApiResponse<PeakIncentiveCampaignDTO> response = new ApiResponse<>();
        try {
            PeakIncentiveCampaignEntity e = peakIncentiveCampaignRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Campaign not found"));
            applyDto(e, dto, false);
            PeakIncentiveCampaignEntity saved = peakIncentiveCampaignRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Campaign updated");
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
    public ApiResponse<List<RiderIncentiveProgressDTO>> riderProgress(Long riderId) {
        ApiResponse<List<RiderIncentiveProgressDTO>> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("riderId is required");
            }
            Instant now = Instant.now();
            List<PeakIncentiveCampaignEntity> active = peakIncentiveCampaignRepository
                    .findByIsActiveTrueAndValidFromLessThanEqualAndValidToGreaterThanEqual(now, now);
            List<RiderIncentiveProgressDTO> data = active.stream()
                    .map(c -> toProgress(riderId, c, now))
                    .collect(Collectors.toList());
            response.setData(data);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(data.size());
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public double resolveBonusForDeliveredOrder(OrderEntity order, Instant deliveredAt) {
        if (order == null || order.getRiderId() == null || deliveredAt == null) {
            return 0.0;
        }
        List<PeakIncentiveCampaignEntity> active = peakIncentiveCampaignRepository
                .findByIsActiveTrueAndValidFromLessThanEqualAndValidToGreaterThanEqual(deliveredAt, deliveredAt);
        double bestBonus = 0.0;
        for (PeakIncentiveCampaignEntity c : active) {
            if (c.getServiceMode() != null && order.getServiceMode() != c.getServiceMode()) {
                continue;
            }
            Window window = resolveWindow(c, deliveredAt);
            if (window == null || deliveredAt.isBefore(window.start()) || deliveredAt.isAfter(window.end())) {
                continue;
            }
            long completed = countDeliveredInWindow(order.getRiderId(), c.getServiceMode(), window.start(), deliveredAt);
            if (completed < Math.max(1, nzInt(c.getMinCompletedOrders()))) {
                continue;
            }
            bestBonus = Math.max(bestBonus, nz(c.getBonusAmount()));
        }
        return round2(bestBonus);
    }

    private RiderIncentiveProgressDTO toProgress(Long riderId, PeakIncentiveCampaignEntity c, Instant now) {
        Window window = resolveWindow(c, now);
        if (window == null) {
            return RiderIncentiveProgressDTO.builder()
                    .campaignId(c.getId())
                    .campaignName(c.getName())
                    .serviceMode(c.getServiceMode())
                    .bonusAmount(round2(nz(c.getBonusAmount())))
                    .minCompletedOrders(Math.max(1, nzInt(c.getMinCompletedOrders())))
                    .completedOrdersInWindow(0L)
                    .remainingOrders(Math.max(1, nzInt(c.getMinCompletedOrders())))
                    .eligibleNow(false)
                    .windowStart(null)
                    .windowEnd(null)
                    .status("CLOSED")
                    .build();
        }
        long completed = countDeliveredInWindow(riderId, c.getServiceMode(), window.start(), now.isAfter(window.end()) ? window.end() : now);
        int minOrders = Math.max(1, nzInt(c.getMinCompletedOrders()));
        int remaining = (int) Math.max(0, minOrders - completed);
        String status;
        if (now.isBefore(window.start())) {
            status = "UPCOMING";
        } else if (now.isAfter(window.end())) {
            status = "CLOSED";
        } else {
            status = "ACTIVE";
        }
        boolean eligibleNow = "ACTIVE".equals(status) && completed >= minOrders;
        return RiderIncentiveProgressDTO.builder()
                .campaignId(c.getId())
                .campaignName(c.getName())
                .serviceMode(c.getServiceMode())
                .bonusAmount(round2(nz(c.getBonusAmount())))
                .minCompletedOrders(minOrders)
                .completedOrdersInWindow(completed)
                .remainingOrders(remaining)
                .eligibleNow(eligibleNow)
                .windowStart(window.start().toString())
                .windowEnd(window.end().toString())
                .status(status)
                .build();
    }

    private long countDeliveredInWindow(Long riderId, ServiceMode serviceMode, Instant from, Instant to) {
        if (serviceMode == null) {
            return orderRepository.countByRiderIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanEqual(
                    riderId, OrderStatus.DELIVERED, from, to);
        }
        return orderRepository.countByRiderIdAndStatusAndServiceModeAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanEqual(
                riderId, OrderStatus.DELIVERED, serviceMode, from, to);
    }

    private Window resolveWindow(PeakIncentiveCampaignEntity c, Instant at) {
        if (c.getStartTimeHhmm() == null || c.getEndTimeHhmm() == null) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime zdt = at.atZone(zone);
        DayOfWeek dow = zdt.getDayOfWeek();
        Set<DayOfWeek> allowedDays = parseDays(c.getDaysOfWeekCsv());
        if (!allowedDays.isEmpty() && !allowedDays.contains(dow)) {
            return null;
        }
        LocalTime start = LocalTime.parse(c.getStartTimeHhmm(), HHMM);
        LocalTime end = LocalTime.parse(c.getEndTimeHhmm(), HHMM);
        LocalDate date = zdt.toLocalDate();
        ZonedDateTime startZdt = date.atTime(start).atZone(zone);
        ZonedDateTime endZdt = date.atTime(end).atZone(zone);
        if (!end.isAfter(start)) {
            endZdt = endZdt.plusDays(1); // overnight slot
        }
        return new Window(startZdt.toInstant(), endZdt.toInstant());
    }

    private Set<DayOfWeek> parseDays(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> DayOfWeek.valueOf(s.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toSet());
    }

    private void applyDto(PeakIncentiveCampaignEntity e, PeakIncentiveCampaignDTO dto, boolean create) {
        if (dto == null) {
            throw new RuntimeException("body is required");
        }
        if (dto.getName() != null) {
            e.setName(trimRequired(dto.getName(), "name"));
        } else if (create) {
            throw new RuntimeException("name is required");
        }
        if (dto.getDescription() != null) {
            e.setDescription(trimToNull(dto.getDescription()));
        }
        if (dto.getServiceMode() != null) {
            e.setServiceMode(dto.getServiceMode());
        }
        if (dto.getBonusAmount() != null) {
            if (dto.getBonusAmount() < 0) {
                throw new RuntimeException("bonusAmount cannot be negative");
            }
            e.setBonusAmount(round2(dto.getBonusAmount()));
        } else if (create) {
            throw new RuntimeException("bonusAmount is required");
        }
        if (dto.getMinCompletedOrders() != null) {
            if (dto.getMinCompletedOrders() <= 0) {
                throw new RuntimeException("minCompletedOrders must be >= 1");
            }
            e.setMinCompletedOrders(dto.getMinCompletedOrders());
        } else if (create) {
            e.setMinCompletedOrders(1);
        }
        if (dto.getIsActive() != null) {
            e.setIsActive(dto.getIsActive());
        } else if (create) {
            e.setIsActive(true);
        }
        if (dto.getValidFrom() != null) {
            e.setValidFrom(Instant.parse(dto.getValidFrom()));
        } else if (create) {
            throw new RuntimeException("validFrom is required");
        }
        if (dto.getValidTo() != null) {
            e.setValidTo(Instant.parse(dto.getValidTo()));
        } else if (create) {
            throw new RuntimeException("validTo is required");
        }
        if (dto.getDaysOfWeek() != null) {
            String csv = dto.getDaysOfWeek().stream()
                    .map(s -> s.trim().toUpperCase(Locale.ROOT))
                    .filter(s -> !s.isBlank())
                    .distinct()
                    .collect(Collectors.joining(","));
            e.setDaysOfWeekCsv(csv.isBlank() ? null : csv);
        }
        if (dto.getStartTimeHhmm() != null) {
            LocalTime.parse(dto.getStartTimeHhmm(), HHMM);
            e.setStartTimeHhmm(dto.getStartTimeHhmm());
        } else if (create) {
            throw new RuntimeException("startTimeHhmm is required (HH:mm)");
        }
        if (dto.getEndTimeHhmm() != null) {
            LocalTime.parse(dto.getEndTimeHhmm(), HHMM);
            e.setEndTimeHhmm(dto.getEndTimeHhmm());
        } else if (create) {
            throw new RuntimeException("endTimeHhmm is required (HH:mm)");
        }
        if (e.getValidFrom() != null && e.getValidTo() != null && !e.getValidTo().isAfter(e.getValidFrom())) {
            throw new RuntimeException("validTo must be after validFrom");
        }
    }

    private PeakIncentiveCampaignDTO toDto(PeakIncentiveCampaignEntity e) {
        List<String> days = e.getDaysOfWeekCsv() == null || e.getDaysOfWeekCsv().isBlank()
                ? List.of()
                : Arrays.stream(e.getDaysOfWeekCsv().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        return PeakIncentiveCampaignDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .description(e.getDescription())
                .serviceMode(e.getServiceMode())
                .bonusAmount(e.getBonusAmount())
                .minCompletedOrders(e.getMinCompletedOrders())
                .isActive(e.getIsActive())
                .validFrom(e.getValidFrom() != null ? e.getValidFrom().toString() : null)
                .validTo(e.getValidTo() != null ? e.getValidTo().toString() : null)
                .daysOfWeek(days)
                .startTimeHhmm(e.getStartTimeHhmm())
                .endTimeHhmm(e.getEndTimeHhmm())
                .build();
    }

    private static String trimRequired(String s, String field) {
        String t = trimToNull(s);
        if (t == null) {
            throw new RuntimeException(field + " is required");
        }
        return t;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static int nzInt(Integer v) {
        return v == null ? 0 : v;
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
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

    private record Window(Instant start, Instant end) {}
}

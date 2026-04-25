package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.incentive.PeakIncentiveCampaignDTO;
import com.youdash.dto.incentive.RiderIncentiveProgressDTO;
import com.youdash.dto.incentive.IncentiveSlabDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.PeakIncentiveCampaignEntity;
import com.youdash.entity.RiderOnlineSessionEntity;
import com.youdash.model.IncentiveType;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.PeakIncentiveCampaignRepository;
import com.youdash.repository.RiderOnlineSessionRepository;
import com.youdash.service.PeakIncentiveService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.temporal.ChronoUnit;
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

    @Autowired
    private RiderOnlineSessionRepository riderOnlineSessionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PeakIncentiveCampaignDTO>> adminList() {
        ApiResponse<List<PeakIncentiveCampaignDTO>> response = new ApiResponse<>();
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
    @Transactional
    public ApiResponse<String> adminDelete(Long id) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("id is required");
            }
            PeakIncentiveCampaignEntity e = peakIncentiveCampaignRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Campaign not found"));
            peakIncentiveCampaignRepository.delete(e);
            response.setData("Deleted");
            response.setMessage("Campaign deleted");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setErr(response, ex.getMessage());
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<PeakIncentiveCampaignDTO>> riderProgress(Long riderId) {
        ApiResponse<List<PeakIncentiveCampaignDTO>> response = new ApiResponse<>();
        if (riderId == null) {
            throw new RuntimeException("riderId is required");
        }
        Instant now = Instant.now();
        List<PeakIncentiveCampaignEntity> active = peakIncentiveCampaignRepository
                .findByIsActiveTrueAndValidFromLessThanEqualAndValidToGreaterThanEqual(now, now);
        List<PeakIncentiveCampaignDTO> data = active.stream()
                .sorted(Comparator.comparing(PeakIncentiveCampaignEntity::getId).reversed())
                .map(this::toDto)
                .collect(Collectors.toList());
        response.setData(data);
        response.setMessage("OK");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        response.setTotalCount(data.size());
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
            if (c.getIncentiveType() == IncentiveType.ONLINE_HOURS_DAILY) {
                continue; // Online-hours incentives are informational; no per-order payout.
            }
            if (c.getServiceMode() != null && order.getServiceMode() != c.getServiceMode()) {
                continue;
            }
            Window window = resolveWindow(c, deliveredAt);
            if (window == null || deliveredAt.isBefore(window.start()) || deliveredAt.isAfter(window.end())) {
                continue;
            }
            long completed = countDeliveredInWindow(order.getRiderId(), c.getServiceMode(), window.start(), deliveredAt);
            double achievedBonus = resolveAchievedBonus(c, completed);
            bestBonus = Math.max(bestBonus, achievedBonus);
        }
        return round2(bestBonus);
    }

    private RiderIncentiveProgressDTO toProgress(Long riderId, PeakIncentiveCampaignEntity c, Instant now) {
        IncentiveType type = normalizeIncentiveType(c.getIncentiveType());
        if (type != IncentiveType.ONLINE_HOURS_DAILY && nzInt(c.getTargetOnlineMinutes()) > 0) {
            type = IncentiveType.ONLINE_HOURS_DAILY;
        }
        if (type == IncentiveType.ONLINE_HOURS_DAILY) {
            return toOnlineHoursProgress(riderId, c, now);
        }

        Window window = resolveWindow(c, now);
        List<IncentiveSlabDTO> slabs = normalizedSlabs(c);
        if (window == null) {
            IncentiveSlabDTO next = slabs.isEmpty() ? null : slabs.get(0);
            return RiderIncentiveProgressDTO.builder()
                    .campaignId(c.getId())
                    .incentiveType(type)
                    .campaignType(type.name())
                    .incentiveTag("DELIVERIES")
                    .campaignName(c.getName())
                    .serviceMode(c.getServiceMode())
                    .incentiveDate(c.getIncentiveDate() != null ? c.getIncentiveDate().toString() : null)
                    .bonusAmount(round2(next != null ? nz(next.getBonusAmount()) : resolveDisplayBonus(c, slabs)))
                    .minCompletedOrders(Math.max(1, nzInt(c.getMinCompletedOrders())))
                    .completedOrdersInWindow(0L)
                    .remainingOrders(Math.max(1, nzInt(c.getMinCompletedOrders())))
                    .nextRequiredDeliveries(next != null ? nzInt(next.getRequiredDeliveries()) : null)
                    .nextBonusAmount(next != null ? round2(nz(next.getBonusAmount())) : null)
                    .slabs(slabs)
                    .eligibleNow(false)
                    .windowStart(null)
                    .windowEnd(null)
                    .status("CLOSED")
                    .build();
        }
        long completed = countDeliveredInWindow(riderId, c.getServiceMode(), window.start(), now.isAfter(window.end()) ? window.end() : now);
        int currentTarget = resolveCurrentTarget(c, slabs);
        int remaining = (int) Math.max(0, currentTarget - completed);
        IncentiveSlabDTO next = resolveNextSlab(slabs, completed);
        double achievedBonus = resolveAchievedBonus(c, completed);
        double displayBonus = achievedBonus > 0
                ? achievedBonus
                : (next != null ? nz(next.getBonusAmount()) : resolveDisplayBonus(c, slabs));
        String status;
        if (now.isBefore(window.start())) {
            status = "UPCOMING";
        } else if (now.isAfter(window.end())) {
            status = "CLOSED";
        } else {
            status = "ACTIVE";
        }
        boolean eligibleNow = "ACTIVE".equals(status) && achievedBonus > 0;
        return RiderIncentiveProgressDTO.builder()
                .campaignId(c.getId())
                .incentiveType(type)
                .campaignType(type.name())
                .incentiveTag("DELIVERIES")
                .campaignName(c.getName())
                .serviceMode(c.getServiceMode())
                .incentiveDate(c.getIncentiveDate() != null ? c.getIncentiveDate().toString() : null)
                .bonusAmount(round2(displayBonus))
                .minCompletedOrders(currentTarget)
                .completedOrdersInWindow(completed)
                .remainingOrders(remaining)
                .nextRequiredDeliveries(next != null ? nzInt(next.getRequiredDeliveries()) : null)
                .nextBonusAmount(next != null ? round2(nz(next.getBonusAmount())) : null)
                .slabs(slabs)
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

    private RiderIncentiveProgressDTO toOnlineHoursProgress(Long riderId, PeakIncentiveCampaignEntity c, Instant now) {
        LocalDate day = c.getIncentiveDate() != null ? c.getIncentiveDate() : now.atZone(ZoneId.systemDefault()).toLocalDate();
        ZoneId zone = ZoneId.systemDefault();
        Instant start = day.atStartOfDay(zone).toInstant();
        Instant end = day.plusDays(1).atStartOfDay(zone).toInstant();
        long seconds = 0L;
        List<RiderOnlineSessionEntity> sessions = riderOnlineSessionRepository.findSessionsOverlappingWindow(riderId, start, end);
        for (RiderOnlineSessionEntity s : sessions) {
            Instant sessionStart = s.getStartedAt();
            Instant sessionEnd = s.getEndedAt() == null ? now : s.getEndedAt();
            if (sessionStart == null || !sessionEnd.isAfter(sessionStart)) continue;
            Instant overlapStart = sessionStart.isAfter(start) ? sessionStart : start;
            Instant overlapEnd = sessionEnd.isBefore(end) ? sessionEnd : end;
            if (overlapEnd.isAfter(overlapStart)) {
                seconds += ChronoUnit.SECONDS.between(overlapStart, overlapEnd);
            }
        }
        int onlineMinutes = (int) Math.max(0L, seconds / 60L);
        int targetMinutes = Math.max(1, nzInt(c.getTargetOnlineMinutes()));
        int remaining = Math.max(0, targetMinutes - onlineMinutes);
        boolean eligible = onlineMinutes >= targetMinutes;
        String status = day.isAfter(now.atZone(zone).toLocalDate()) ? "UPCOMING" :
                (day.isBefore(now.atZone(zone).toLocalDate()) ? "CLOSED" : "ACTIVE");

        return RiderIncentiveProgressDTO.builder()
                .campaignId(c.getId())
                .incentiveType(IncentiveType.ONLINE_HOURS_DAILY)
                .campaignType(IncentiveType.ONLINE_HOURS_DAILY.name())
                .incentiveTag("HOURS")
                .campaignName(c.getName())
                .incentiveDate(day.toString())
                .serviceMode(c.getServiceMode())
                .targetOnlineMinutes(targetMinutes)
                .completedOnlineMinutes(onlineMinutes)
                .bonusAmount(round2(nz(c.getBonusAmount())))
                .remainingOrders(remaining)
                .slabs(List.of())
                .eligibleNow(eligible)
                .windowStart(start.toString())
                .windowEnd(end.toString())
                .status(status)
                .build();
    }

    private List<IncentiveSlabDTO> normalizedSlabs(PeakIncentiveCampaignEntity c) {
        try {
            if (c.getSlabsJson() == null || c.getSlabsJson().isBlank()) return List.of();
            List<IncentiveSlabDTO> slabs = objectMapper.readValue(
                    c.getSlabsJson(),
                    new TypeReference<List<IncentiveSlabDTO>>() {}
            );
            return slabs.stream()
                    .filter(s -> s != null && nzInt(s.getRequiredDeliveries()) > 0 && nz(s.getBonusAmount()) >= 0)
                    .sorted(Comparator.comparingInt(s -> nzInt(s.getRequiredDeliveries())))
                    .collect(Collectors.toList());
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private double resolveAchievedBonus(PeakIncentiveCampaignEntity c, long completed) {
        List<IncentiveSlabDTO> slabs = normalizedSlabs(c);
        if (!slabs.isEmpty()) {
            double best = 0.0;
            for (IncentiveSlabDTO s : slabs) {
                if (completed >= nzInt(s.getRequiredDeliveries())) {
                    best = Math.max(best, nz(s.getBonusAmount()));
                }
            }
            return round2(best);
        }
        int minOrders = Math.max(1, nzInt(c.getMinCompletedOrders()));
        return completed >= minOrders ? round2(nz(c.getBonusAmount())) : 0.0;
    }

    private double resolveDisplayBonus(PeakIncentiveCampaignEntity c, List<IncentiveSlabDTO> slabs) {
        if (slabs == null || slabs.isEmpty()) {
            return nz(c.getBonusAmount());
        }
        double best = 0.0;
        for (IncentiveSlabDTO slab : slabs) {
            best = Math.max(best, nz(slab.getBonusAmount()));
        }
        return best;
    }

    private IncentiveSlabDTO resolveNextSlab(List<IncentiveSlabDTO> slabs, long completed) {
        if (slabs == null || slabs.isEmpty()) return null;
        for (IncentiveSlabDTO s : slabs) {
            if (completed < nzInt(s.getRequiredDeliveries())) {
                return s;
            }
        }
        return null;
    }

    private int resolveCurrentTarget(PeakIncentiveCampaignEntity c, List<IncentiveSlabDTO> slabs) {
        if (slabs == null || slabs.isEmpty()) {
            return Math.max(1, nzInt(c.getMinCompletedOrders()));
        }
        return nzInt(slabs.get(0).getRequiredDeliveries());
    }

    private Window resolveWindow(PeakIncentiveCampaignEntity c, Instant at) {
        if (c.getStartTimeHhmm() == null || c.getEndTimeHhmm() == null) {
            return null;
        }
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime zdt = at.atZone(zone);
        LocalDate baseDate = c.getIncentiveDate() != null ? c.getIncentiveDate() : zdt.toLocalDate();
        DayOfWeek dow = baseDate.getDayOfWeek();
        Set<DayOfWeek> allowedDays = parseDays(c.getDaysOfWeekCsv());
        if (!allowedDays.isEmpty() && !allowedDays.contains(dow)) {
            return null;
        }
        LocalTime start = LocalTime.parse(c.getStartTimeHhmm(), HHMM);
        LocalTime end = LocalTime.parse(c.getEndTimeHhmm(), HHMM);
        ZonedDateTime startZdt = baseDate.atTime(start).atZone(zone);
        ZonedDateTime endZdt = baseDate.atTime(end).atZone(zone);
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
        if (dto.getIncentiveType() != null) {
            e.setIncentiveType(dto.getIncentiveType());
        } else if (create) {
            throw new RuntimeException("incentiveType is required");
        }
        if (dto.getName() != null) {
            e.setName(trimRequired(dto.getName(), "name"));
        } else if (create) {
            e.setName(defaultCampaignName(e, dto));
        }
        if (dto.getDescription() != null) {
            e.setDescription(trimToNull(dto.getDescription()));
        }
        if (dto.getServiceMode() != null) {
            e.setServiceMode(dto.getServiceMode());
        }
        if (dto.getIncentiveDate() != null) {
            e.setIncentiveDate(LocalDate.parse(dto.getIncentiveDate()));
        }
        if (dto.getTargetOnlineMinutes() != null) {
            if (dto.getTargetOnlineMinutes() <= 0) {
                throw new RuntimeException("targetOnlineMinutes must be >= 1");
            }
            e.setTargetOnlineMinutes(dto.getTargetOnlineMinutes());
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
        if (dto.getSlabs() != null) {
            List<IncentiveSlabDTO> normalized = dto.getSlabs().stream()
                    .filter(s -> s != null && nzInt(s.getRequiredDeliveries()) > 0 && nz(s.getBonusAmount()) >= 0)
                    .sorted(Comparator.comparingInt(s -> nzInt(s.getRequiredDeliveries())))
                    .collect(Collectors.toList());
            if (!normalized.isEmpty()) {
                try {
                    e.setSlabsJson(objectMapper.writeValueAsString(normalized));
                } catch (Exception ex) {
                    throw new RuntimeException("Invalid slabs payload");
                }
                e.setMinCompletedOrders(nzInt(normalized.get(0).getRequiredDeliveries()));
                double maxBonus = normalized.stream().mapToDouble(s -> nz(s.getBonusAmount())).max().orElse(0.0);
                e.setBonusAmount(round2(maxBonus));
            } else {
                e.setSlabsJson(null);
            }
        }
        IncentiveType type = normalizeIncentiveType(e.getIncentiveType());
        e.setIncentiveType(type);
        if (type == IncentiveType.ONLINE_HOURS_DAILY) {
            if (e.getTargetOnlineMinutes() == null || e.getTargetOnlineMinutes() <= 0) {
                throw new RuntimeException("targetOnlineMinutes is required for ONLINE_HOURS_DAILY");
            }
            if (e.getIncentiveDate() == null) {
                throw new RuntimeException("incentiveDate is required for ONLINE_HOURS_DAILY");
            }
            e.setMinCompletedOrders(1);
            e.setSlabsJson(null);
            if (e.getStartTimeHhmm() == null) e.setStartTimeHhmm("00:00");
            if (e.getEndTimeHhmm() == null) e.setEndTimeHhmm("23:59");
            if (e.getValidFrom() == null || e.getValidTo() == null) {
                ZoneId zone = ZoneId.systemDefault();
                Instant from = e.getIncentiveDate().atStartOfDay(zone).toInstant();
                Instant to = e.getIncentiveDate().plusDays(1).atStartOfDay(zone).minusSeconds(1).toInstant();
                e.setValidFrom(from);
                e.setValidTo(to);
            }
        } else {
            if (e.getIncentiveDate() == null) {
                throw new RuntimeException("incentiveDate is required for DAILY_DELIVERIES_SLOT");
            }
            ZoneId zone = ZoneId.systemDefault();
            LocalTime start = LocalTime.parse(e.getStartTimeHhmm(), HHMM);
            LocalTime end = LocalTime.parse(e.getEndTimeHhmm(), HHMM);
            Instant from = e.getIncentiveDate().atTime(start).atZone(zone).toInstant();
            Instant to = e.getIncentiveDate().atTime(end).atZone(zone).toInstant();
            if (!end.isAfter(start)) {
                to = e.getIncentiveDate().plusDays(1).atTime(end).atZone(zone).toInstant();
            }
            e.setValidFrom(from);
            e.setValidTo(to);
        }
        if (e.getValidFrom() != null && e.getValidTo() != null && !e.getValidTo().isAfter(e.getValidFrom())) {
            throw new RuntimeException("validTo must be after validFrom");
        }
    }

    private PeakIncentiveCampaignDTO toDto(PeakIncentiveCampaignEntity e) {
        List<String> days = e.getDaysOfWeekCsv() == null || e.getDaysOfWeekCsv().isBlank()
                ? List.of()
                : Arrays.stream(e.getDaysOfWeekCsv().split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
        List<IncentiveSlabDTO> slabs = normalizedSlabs(e);
        return PeakIncentiveCampaignDTO.builder()
                .id(e.getId())
                .incentiveType(normalizeIncentiveType(e.getIncentiveType()))
                .name(e.getName())
                .description(e.getDescription())
                .serviceMode(e.getServiceMode())
                .bonusAmount(e.getBonusAmount())
                .minCompletedOrders(e.getMinCompletedOrders())
                .incentiveDate(e.getIncentiveDate() != null ? e.getIncentiveDate().toString() : null)
                .targetOnlineMinutes(e.getTargetOnlineMinutes())
                .slabs(slabs)
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

    private String defaultCampaignName(PeakIncentiveCampaignEntity e, PeakIncentiveCampaignDTO dto) {
        IncentiveType type = normalizeIncentiveType(
                dto != null && dto.getIncentiveType() != null ? dto.getIncentiveType() : e.getIncentiveType()
        );
        String date = dto != null && dto.getIncentiveDate() != null ? dto.getIncentiveDate() : "campaign";
        if (type == IncentiveType.ONLINE_HOURS_DAILY) {
            return "Online Hours " + date;
        }
        String start = dto != null && dto.getStartTimeHhmm() != null ? dto.getStartTimeHhmm() : "slot";
        String end = dto != null && dto.getEndTimeHhmm() != null ? dto.getEndTimeHhmm() : "slot";
        return "Delivery Slot " + date + " " + start + "-" + end;
    }

    private IncentiveType normalizeIncentiveType(IncentiveType raw) {
        if (raw == null || raw == IncentiveType.BONUS || raw == IncentiveType.DAILY) {
            return IncentiveType.DAILY_DELIVERIES_SLOT;
        }
        return raw;
    }

    private record Window(Instant start, Instant end) {}
}

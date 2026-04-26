package com.youdash.service.impl;

import com.youdash.dto.DeliveryPromiseDTO;
import com.youdash.entity.HubRouteSlaEntity;
import com.youdash.repository.HubRouteSlaRepository;
import com.youdash.service.DeliveryPromiseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class DeliveryPromiseServiceImpl implements DeliveryPromiseService {

    private static final String NEXT_DAY = "NEXT_DAY";
    private static final String HOURS    = "HOURS";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Autowired
    private HubRouteSlaRepository slaRepository;

    @Override
    public DeliveryPromiseDTO getDeliveryPromise(Long hubRouteId, String deliveryTypeUI) {
        if (hubRouteId == null) {
            return new DeliveryPromiseDTO(
                    "Delivery timeline will be confirmed after hub selection.",
                    "Please select origin and destination hubs on an active route.",
                    null,
                    false);
        }

        LocalDateTime now         = LocalDateTime.now(BUSINESS_ZONE);
        LocalTime    currentTime  = now.toLocalTime();

        List<HubRouteSlaEntity> slaList =
                slaRepository.findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(hubRouteId);
        if (slaList.isEmpty()) {
            return new DeliveryPromiseDTO(
                    "Delivery schedule will be confirmed at booking.",
                    null, null, false);
        }

        // 1) First slot whose cutoff has not yet passed today
        for (HubRouteSlaEntity sla : slaList) {
            if (sla.getCutoffTime() == null) continue;
            if (!currentTime.isBefore(sla.getCutoffTime())) continue;

            String type = normalizeType(sla.getDeliveryType());
            if (NEXT_DAY.equals(type)) return buildNextDayPromise(now, sla, deliveryTypeUI, false);
            if (HOURS.equals(type))    return buildHoursPromise(sla);
        }

        // 2) All today's cutoffs missed — shift to tomorrow's first NEXT_DAY window
        HubRouteSlaEntity firstNextDay = slaList.stream()
                .filter(s -> NEXT_DAY.equalsIgnoreCase(normalizeType(s.getDeliveryType())))
                .findFirst()
                .orElse(null);
        if (firstNextDay != null) return buildNextDayPromise(now, firstNextDay, deliveryTypeUI, true);

        // 3) HOURS-only fallback
        HubRouteSlaEntity fallback = slaList.stream()
                .filter(s -> HOURS.equalsIgnoreCase(normalizeType(s.getDeliveryType())))
                .findFirst()
                .orElse(null);
        if (fallback != null) return buildHoursPromise(fallback);

        return new DeliveryPromiseDTO(
                "No delivery commitment is configured for this route.", null, null, false);
    }

    // ── Builders ──────────────────────────────────────────────────────────────

    private DeliveryPromiseDTO buildNextDayPromise(
            LocalDateTime now, HubRouteSlaEntity sla, String deliveryTypeUI, boolean shifted) {

        if (sla.getDeliveryTime() == null) {
            return new DeliveryPromiseDTO(
                    "Delivery schedule will be confirmed at booking.", null, null, shifted);
        }

        /*
         * handoverDate = the day the parcel must reach the origin hub
         *   active (not shifted) → today
         *   shifted              → tomorrow  (today's slots are all closed)
         *
         * deliveryDate = handoverDate + 1 day  (next-day commitment)
         */
        LocalDate handoverDate  = shifted ? now.toLocalDate().plusDays(1) : now.toLocalDate();
        LocalDate deliveryDate  = handoverDate.plusDays(1);

        String cutoffText      = sla.getCutoffTime() != null
                ? formatTime(sla.getCutoffTime()) : "the scheduled cutoff";
        String deliveryTimeText = formatTime(sla.getDeliveryTime());

        if (shifted) {
            // e.g. "Today's delivery slots have closed. Your parcel can be delivered by 28 Apr 2026 at 12:00 PM."
            String deliveryDateStr  = formatDate(deliveryDate);
            String handoverDateStr  = formatDate(handoverDate);
            String deliveredBy      = "Delivered by " + deliveryDateStr + " at " + deliveryTimeText;
            String message          = "Today's delivery slots have closed. "
                    + "Your parcel can be delivered by " + deliveryDateStr + " at " + deliveryTimeText + ".";
            String cutoffInfo       = buildShiftedCutoffInfo(deliveryTypeUI, cutoffText, handoverDateStr);
            return new DeliveryPromiseDTO(message, cutoffInfo, deliveredBy, true);
        } else {
            // e.g. "Delivered by tomorrow, 12:00 PM"
            String deliveredBy = "Delivered by tomorrow, " + deliveryTimeText;
            String cutoffInfo  = buildActiveCutoffInfo(deliveryTypeUI, cutoffText);
            return new DeliveryPromiseDTO(deliveredBy, cutoffInfo, deliveredBy, false);
        }
    }

    private DeliveryPromiseDTO buildHoursPromise(HubRouteSlaEntity sla) {
        int h = sla.getDeliveredWithinHours() != null ? sla.getDeliveredWithinHours() : 0;
        String msg = h > 0
                ? "Delivered within " + h + " hour" + (h == 1 ? "" : "s") + "."
                : "Delivery window will be confirmed at booking.";
        return new DeliveryPromiseDTO(msg, null, msg, false);
    }

    // ── Cutoff-info helpers ───────────────────────────────────────────────────

    /**
     * Active slot — parcel must reach origin hub before {@code cutoffText} today.
     */
    private static String buildActiveCutoffInfo(String deliveryTypeUI, String cutoffText) {
        if (isHubDrop(deliveryTypeUI)) {
            return "Drop your parcel at the origin hub before " + cutoffText
                    + " today to secure this delivery slot.";
        }
        return "Our rider will collect your parcel before " + cutoffText
                + " today to ensure it reaches the origin hub on time.";
    }

    /**
     * Shifted — today's slots have closed; next window is on {@code handoverDateStr}.
     */
    private static String buildShiftedCutoffInfo(
            String deliveryTypeUI, String cutoffText, String handoverDateStr) {
        if (isHubDrop(deliveryTypeUI)) {
            return "Drop your parcel at the origin hub before " + cutoffText
                    + " on " + handoverDateStr + " to meet this deadline.";
        }
        return "A pickup will be arranged before " + cutoffText + " on " + handoverDateStr
                + " to dispatch your parcel to the origin hub.";
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private static boolean isHubDrop(String deliveryTypeUI) {
        String t = normalizeUiType(deliveryTypeUI);
        return "HUB_TO_DOOR".equals(t) || "HUB_TO_HUB".equals(t);
    }

    private static String normalizeUiType(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeType(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static String formatTime(LocalTime time) {
        return time == null ? "" : time.format(DISPLAY_TIME);
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }
}

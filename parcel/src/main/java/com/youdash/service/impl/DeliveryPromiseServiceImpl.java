package com.youdash.service.impl;

import com.youdash.dto.DeliveryPromiseDTO;
import com.youdash.entity.HubRouteSlaEntity;
import com.youdash.repository.HubRouteSlaRepository;
import com.youdash.service.DeliveryPromiseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class DeliveryPromiseServiceImpl implements DeliveryPromiseService {

    private static final String NEXT_DAY = "NEXT_DAY";
    private static final String HOURS = "HOURS";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_DATE_TIME_NUMERIC = DateTimeFormatter.ofPattern("d-M-yyyy h:mm a", Locale.ENGLISH);

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

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalTime currentTime = now.toLocalTime();

        List<HubRouteSlaEntity> slaList =
                slaRepository.findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(hubRouteId);
        if (slaList.isEmpty()) {
            return new DeliveryPromiseDTO(
                    "Delivery schedule will be confirmed at booking.",
                    null,
                    null,
                    false);
        }

        // 1) First SLA whose cutoff is not passed (same-day window)
        for (HubRouteSlaEntity sla : slaList) {
            if (sla.getCutoffTime() == null) {
                continue;
            }
            if (!currentTime.isBefore(sla.getCutoffTime())) {
                continue;
            }
            String dt = normalizeType(sla.getDeliveryType());
            if (NEXT_DAY.equals(dt)) {
                return buildNextDayPromise(sla, deliveryTypeUI, false);
            }
            if (HOURS.equals(dt)) {
                return buildHoursPromise(sla);
            }
        }

        // 2) Missed all cutoffs → next available NEXT_DAY rule (tomorrow)
        HubRouteSlaEntity firstNextDay = slaList.stream()
                .filter(s -> NEXT_DAY.equalsIgnoreCase(normalizeType(s.getDeliveryType())))
                .findFirst()
                .orElse(null);

        if (firstNextDay != null) {
            return buildNextDayPromise(firstNextDay, deliveryTypeUI, true);
        }

        // 3) HOURS fallback (e.g. no NEXT_DAY configured)
        HubRouteSlaEntity fallback = slaList.stream()
                .filter(s -> HOURS.equalsIgnoreCase(normalizeType(s.getDeliveryType())))
                .findFirst()
                .orElse(null);

        if (fallback != null) {
            return buildHoursPromise(fallback);
        }

        return new DeliveryPromiseDTO("No delivery commitment is configured for this route.", null, null, false);
    }

    private DeliveryPromiseDTO buildNextDayPromise(
            HubRouteSlaEntity sla, String deliveryTypeUI, boolean shifted) {

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalDateTime handoverDateTime = now.toLocalDate().atStartOfDay();
        if (shifted) {
            handoverDateTime = handoverDateTime.plusDays(1);
        }

        String cutoffText = sla.getCutoffTime() != null
                ? formatTime(sla.getCutoffTime())
                : "scheduled cutoff";

        LocalDateTime deliveryDateTime = null;
        if (sla.getDeliveryTime() != null) {
            // For a selected slot, promise is "next day" from that slot's day.
            deliveryDateTime = handoverDateTime.plusDays(1).with(sla.getDeliveryTime());
        }

        String deliveredBy = deliveryDateTime != null
                ? ("Delivered by " + (shifted
                    ? formatDateTimeNumeric(deliveryDateTime)
                    : ("next day " + formatTime(deliveryDateTime.toLocalTime()))))
                : "Delivery schedule will be confirmed at booking.";

        String handoverDateText = shifted ? formatDate(handoverDateTime) : "";
        String cutoffInfo = buildCutoffInfo(deliveryTypeUI, cutoffText, deliveredBy, shifted, handoverDateText);
        String message = shifted
                ? ("Today's delivery slot is missed. Next available slot is on " + handoverDateText + " before " + cutoffText + ".")
                : deliveredBy;

        return new DeliveryPromiseDTO(message, cutoffInfo, deliveredBy, shifted);
    }

    private DeliveryPromiseDTO buildHoursPromise(HubRouteSlaEntity sla) {
        int h = sla.getDeliveredWithinHours() != null ? sla.getDeliveredWithinHours() : 0;
        String message = h > 0 ? "Delivered within " + h + " hours." : "Delivery window will be confirmed at booking.";
        return new DeliveryPromiseDTO(message, null, message, false);
    }

    private static String buildCutoffInfo(
            String deliveryTypeUI, String cutoffText, String deliveredBy, boolean shifted, String handoverDateText) {
        String type = normalizeUiType(deliveryTypeUI);
        if ("HUB_TO_DOOR".equals(type)) {
            if (shifted) {
                return "Today's delivery slot is missed. Please hand over the parcel at the hub on "
                        + handoverDateText + " before " + cutoffText + ".";
            }
            return "Available slot today: please hand over the parcel at the hub before " + cutoffText + ".";
        }

        if (shifted) {
            return "Today's delivery slot is missed. Pickup will be initiated on "
                    + handoverDateText + " before " + cutoffText + ".";
        }
        return "Available slot today: our rider will pick up the parcel before " + cutoffText + ".";
    }

    private static String normalizeUiType(String deliveryTypeUI) {
        if (deliveryTypeUI == null) {
            return "";
        }
        return deliveryTypeUI.trim().toUpperCase(Locale.ROOT);
    }

    private static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DISPLAY_TIME);
    }

    private static String formatDateTimeNumeric(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(DISPLAY_DATE_TIME_NUMERIC);
    }

    private static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.toLocalDate().format(DISPLAY_DATE);
    }

    private static String normalizeType(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}

package com.youdash.service.impl;

import com.youdash.dto.DeliveryPromiseDTO;
import com.youdash.entity.HubRouteSlaEntity;
import com.youdash.repository.HubRouteSlaRepository;
import com.youdash.service.DeliveryPromiseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class DeliveryPromiseServiceImpl implements DeliveryPromiseService {

    private static final String NEXT_DAY = "NEXT_DAY";
    private static final String HOURS = "HOURS";

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    @Autowired
    private HubRouteSlaRepository slaRepository;

    @Override
    public DeliveryPromiseDTO getDeliveryPromise(Long hubRouteId, String deliveryTypeUI) {
        if (hubRouteId == null) {
            return new DeliveryPromiseDTO(
                    "Delivery time confirmed after hub selection",
                    "Select origin and destination hubs with a configured route",
                    false);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();

        List<HubRouteSlaEntity> slaList =
                slaRepository.findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(hubRouteId);
        if (slaList.isEmpty()) {
            return new DeliveryPromiseDTO(
                    "Delivery schedule on confirmation",
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

        return new DeliveryPromiseDTO("No delivery promise configured", null, false);
    }

    private DeliveryPromiseDTO buildNextDayPromise(
            HubRouteSlaEntity sla, String deliveryTypeUI, boolean shifted) {

        String deliveryPart = sla.getDeliveryTime() != null
                ? formatTime(sla.getDeliveryTime())
                : "next day";

        String message = "Delivery by Tomorrow " + deliveryPart;

        String action = getActionWord(deliveryTypeUI);
        String cutoffInfo;
        if (shifted) {
            cutoffInfo = "Next available slot";
        } else if (sla.getCutoffTime() != null) {
            cutoffInfo = action + " before " + formatTime(sla.getCutoffTime());
        } else {
            cutoffInfo = null;
        }

        return new DeliveryPromiseDTO(message, cutoffInfo, shifted);
    }

    private DeliveryPromiseDTO buildHoursPromise(HubRouteSlaEntity sla) {
        int h = sla.getDeliveredWithinHours() != null ? sla.getDeliveredWithinHours() : 0;
        String message = h > 0 ? "Delivered within " + h + " hours" : "Delivery window on confirmation";
        return new DeliveryPromiseDTO(message, null, false);
    }

    private static String getActionWord(String deliveryTypeUI) {
        if (deliveryTypeUI == null) {
            return "Pickup";
        }
        switch (deliveryTypeUI.trim().toUpperCase(Locale.ROOT)) {
            case "HUB_TO_DOOR":
                return "Drop";
            case "DOOR_TO_HUB":
            case "DOOR_TO_DOOR":
            default:
                return "Pickup";
        }
    }

    private static String formatTime(LocalTime time) {
        if (time == null) {
            return "";
        }
        return time.format(DISPLAY_TIME);
    }

    private static String normalizeType(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }
}

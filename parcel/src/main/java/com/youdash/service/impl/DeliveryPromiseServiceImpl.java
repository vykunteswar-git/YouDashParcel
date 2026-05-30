package com.youdash.service.impl;

import com.youdash.dto.DeliveryPromiseDTO;
import com.youdash.entity.HubEntity;
import com.youdash.entity.HubCorridorSlaEntity;
import com.youdash.entity.HubRouteSlaEntity;
import com.youdash.entity.ZoneRouteSlaEntity;
import com.youdash.repository.HubCorridorSlaRepository;
import com.youdash.repository.HubRepository;
import com.youdash.repository.HubRouteSlaRepository;
import com.youdash.repository.ZoneRouteSlaRepository;
import com.youdash.service.DeliveryPromiseService;
import com.youdash.service.RouteRateResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DeliveryPromiseServiceImpl implements DeliveryPromiseService {

    private static final String NEXT_DAY = "NEXT_DAY";
    private static final String HOURS = "HOURS";
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Kolkata");

    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    @Autowired
    private HubRouteSlaRepository hubRouteSlaRepository;

    @Autowired
    private HubCorridorSlaRepository hubCorridorSlaRepository;

    @Autowired
    private ZoneRouteSlaRepository zoneRouteSlaRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private RouteRateResolver routeRateResolver;

    @Override
    public DeliveryPromiseDTO getDeliveryPromise(Long hubRouteId, String deliveryTypeUI) {
        return getOutstationDeliveryPromise(null, null, hubRouteId, deliveryTypeUI);
    }

    @Override
    public DeliveryPromiseDTO getOutstationDeliveryPromise(
            Long originHubId,
            Long destinationHubId,
            Long hubRouteId,
            String deliveryTypeUI) {

        LocalTime hubIntakeCutoff = null;
        if (originHubId != null) {
            hubIntakeCutoff = hubRepository.findById(originHubId)
                    .map(HubEntity::getIntakeCutoff)
                    .orElse(null);
        }

        Long destinationZoneId = null;
        if (destinationHubId != null) {
            destinationZoneId = hubRepository.findById(destinationHubId)
                    .map(h -> h.getZoneId())
                    .orElse(null);
        }

        Long zoneRouteId = null;
        if (originHubId != null && destinationHubId != null) {
            zoneRouteId = routeRateResolver.resolveZoneRouteId(originHubId, destinationHubId).orElse(null);
        }

        List<SlaSlot> slots = new ArrayList<>();
        if (originHubId != null && destinationZoneId != null) {
            for (HubCorridorSlaEntity sla : hubCorridorSlaRepository
                    .findByHubIdAndDestinationZoneIdAndIsActiveTrueOrderByPriorityAsc(
                            originHubId, destinationZoneId)) {
                slots.add(SlaSlot.fromHubCorridor(sla));
            }
        }
        if (slots.isEmpty() && zoneRouteId != null) {
            for (ZoneRouteSlaEntity sla : zoneRouteSlaRepository
                    .findByZoneRouteIdAndIsActiveTrueOrderByPriorityAsc(zoneRouteId)) {
                slots.add(SlaSlot.fromZone(sla));
            }
        }
        if (slots.isEmpty() && hubRouteId != null) {
            for (HubRouteSlaEntity sla : hubRouteSlaRepository
                    .findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(hubRouteId)) {
                slots.add(SlaSlot.fromHub(sla));
            }
        }

        if (slots.isEmpty()) {
            if (zoneRouteId == null && hubRouteId == null) {
                return new DeliveryPromiseDTO(
                        "Delivery timeline will be confirmed after hub selection.",
                        "Please select origin and destination hubs on an active route.",
                        null,
                        false);
            }
            return new DeliveryPromiseDTO(
                    "Delivery schedule will be confirmed at booking.",
                    hubIntakeCutoff != null
                            ? "Hand over at the origin hub before " + formatTime(hubIntakeCutoff) + "."
                            : null,
                    null,
                    false);
        }

        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        LocalTime currentTime = now.toLocalTime();

        for (SlaSlot sla : slots) {
            LocalTime intakeGate = hubIntakeCutoff != null ? hubIntakeCutoff : sla.cutoffTime;
            if (intakeGate == null) {
                continue;
            }
            if (!currentTime.isBefore(intakeGate)) {
                continue;
            }

            String type = normalizeType(sla.deliveryType);
            if (NEXT_DAY.equals(type)) {
                return buildNextDayPromise(now, sla, deliveryTypeUI, false, intakeGate);
            }
            if (HOURS.equals(type)) {
                return buildHoursPromise(sla);
            }
        }

        SlaSlot firstNextDay = slots.stream()
                .filter(s -> NEXT_DAY.equalsIgnoreCase(normalizeType(s.deliveryType)))
                .findFirst()
                .orElse(null);
        if (firstNextDay != null) {
            LocalTime intakeGate = hubIntakeCutoff != null ? hubIntakeCutoff : firstNextDay.cutoffTime;
            return buildNextDayPromise(now, firstNextDay, deliveryTypeUI, true,
                    intakeGate != null ? intakeGate : LocalTime.of(23, 59));
        }

        SlaSlot fallback = slots.stream()
                .filter(s -> HOURS.equalsIgnoreCase(normalizeType(s.deliveryType)))
                .findFirst()
                .orElse(null);
        if (fallback != null) {
            return buildHoursPromise(fallback);
        }

        return new DeliveryPromiseDTO(
                "No delivery commitment is configured for this route.", null, null, false);
    }

    private DeliveryPromiseDTO buildNextDayPromise(
            LocalDateTime now,
            SlaSlot sla,
            String deliveryTypeUI,
            boolean shifted,
            LocalTime intakeCutoffForCopy) {

        if (sla.deliveryTime == null) {
            return new DeliveryPromiseDTO(
                    "Delivery schedule will be confirmed at booking.", null, null, shifted);
        }

        LocalDate handoverDate = shifted ? now.toLocalDate().plusDays(1) : now.toLocalDate();
        LocalDate deliveryDate = handoverDate.plusDays(1);

        String cutoffText = formatTime(intakeCutoffForCopy);
        String deliveryTimeText = formatTime(sla.deliveryTime);

        if (shifted) {
            String deliveryDateStr = formatDate(deliveryDate);
            String handoverDateStr = formatDate(handoverDate);
            String deliveredBy = "Delivered by " + deliveryDateStr + " at " + deliveryTimeText;
            String message = "Today's delivery slots have closed. "
                    + "Your parcel can be delivered by " + deliveryDateStr + " at " + deliveryTimeText + ".";
            String cutoffInfo = buildShiftedCutoffInfo(deliveryTypeUI, cutoffText, handoverDateStr);
            return new DeliveryPromiseDTO(message, cutoffInfo, deliveredBy, true);
        }

        String deliveredBy = "Delivered by tomorrow, " + deliveryTimeText;
        String cutoffInfo = buildActiveCutoffInfo(deliveryTypeUI, cutoffText);
        return new DeliveryPromiseDTO(deliveredBy, cutoffInfo, deliveredBy, false);
    }

    private DeliveryPromiseDTO buildHoursPromise(SlaSlot sla) {
        int h = sla.deliveredWithinHours != null ? sla.deliveredWithinHours : 0;
        String msg = h > 0
                ? "Delivered within " + h + " hour" + (h == 1 ? "" : "s") + "."
                : "Delivery window will be confirmed at booking.";
        return new DeliveryPromiseDTO(msg, null, msg, false);
    }

    private static String buildActiveCutoffInfo(String deliveryTypeUI, String cutoffText) {
        if (isHubDrop(deliveryTypeUI)) {
            return "Drop your parcel at the origin hub before " + cutoffText + " today to secure this delivery slot.";
        }
        return "Our rider will collect your parcel before " + cutoffText
                + " today to ensure it reaches the origin hub on time.";
    }

    private static String buildShiftedCutoffInfo(
            String deliveryTypeUI, String cutoffText, String handoverDateStr) {
        if (isHubDrop(deliveryTypeUI)) {
            return "Drop your parcel at the origin hub before " + cutoffText
                    + " on " + handoverDateStr + " to meet this deadline.";
        }
        return "A pickup will be arranged before " + cutoffText + " on " + handoverDateStr
                + " to dispatch your parcel to the origin hub.";
    }

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

    private static final class SlaSlot {
        final String deliveryType;
        final LocalTime cutoffTime;
        final LocalTime deliveryTime;
        final Integer deliveredWithinHours;

        SlaSlot(String deliveryType, LocalTime cutoffTime, LocalTime deliveryTime, Integer deliveredWithinHours) {
            this.deliveryType = deliveryType;
            this.cutoffTime = cutoffTime;
            this.deliveryTime = deliveryTime;
            this.deliveredWithinHours = deliveredWithinHours;
        }

        static SlaSlot fromZone(ZoneRouteSlaEntity e) {
            return new SlaSlot(e.getDeliveryType(), e.getCutoffTime(), e.getDeliveryTime(), e.getDeliveredWithinHours());
        }

        static SlaSlot fromHub(HubRouteSlaEntity e) {
            return new SlaSlot(e.getDeliveryType(), e.getCutoffTime(), e.getDeliveryTime(), e.getDeliveredWithinHours());
        }

        static SlaSlot fromHubCorridor(HubCorridorSlaEntity e) {
            return new SlaSlot(e.getDeliveryType(), e.getCutoffTime(), e.getDeliveryTime(), e.getDeliveredWithinHours());
        }
    }
}

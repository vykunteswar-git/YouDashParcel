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
                    .map(HubEntity::getZoneId)
                    .orElse(null);
        }

        Long zoneRouteId = null;
        if (originHubId != null && destinationHubId != null) {
            zoneRouteId = routeRateResolver.resolveZoneRouteId(originHubId, destinationHubId).orElse(null);
        }

        boolean perSlotDeparture = false;
        List<SlaSlot> slots = new ArrayList<>();
        if (originHubId != null && destinationZoneId != null) {
            List<HubCorridorSlaEntity> hubCorridor = hubCorridorSlaRepository
                    .findByHubIdAndDestinationZoneIdAndIsActiveTrueOrderByPriorityAsc(
                            originHubId, destinationZoneId);
            if (!hubCorridor.isEmpty()) {
                perSlotDeparture = true;
                for (HubCorridorSlaEntity sla : hubCorridor) {
                    slots.add(SlaSlot.fromHubCorridor(sla));
                }
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
            LocalTime departureGate = resolveDepartureGate(perSlotDeparture, hubIntakeCutoff, sla);
            if (departureGate == null) {
                continue;
            }
            if (!currentTime.isBefore(departureGate)) {
                continue;
            }

            String type = normalizeType(sla.deliveryType);
            if (NEXT_DAY.equals(type)) {
                return buildScheduledPromise(now, sla, deliveryTypeUI, false, departureGate);
            }
            if (HOURS.equals(type)) {
                return buildHoursPromise(sla, departureGate);
            }
        }

        SlaSlot firstNextDay = slots.stream()
                .filter(s -> NEXT_DAY.equalsIgnoreCase(normalizeType(s.deliveryType)))
                .findFirst()
                .orElse(null);
        if (firstNextDay != null) {
            LocalTime departureGate = resolveDepartureGate(perSlotDeparture, hubIntakeCutoff, firstNextDay);
            if (departureGate == null) {
                departureGate = LocalTime.of(23, 59);
            }
            return buildScheduledPromise(now, firstNextDay, deliveryTypeUI, true, departureGate);
        }

        SlaSlot fallback = slots.stream()
                .filter(s -> HOURS.equalsIgnoreCase(normalizeType(s.deliveryType)))
                .findFirst()
                .orElse(null);
        if (fallback != null) {
            LocalTime departureGate = resolveDepartureGate(perSlotDeparture, hubIntakeCutoff, fallback);
            return buildHoursPromise(fallback, departureGate != null ? departureGate : LocalTime.MIDNIGHT);
        }

        return new DeliveryPromiseDTO(
                "No delivery commitment is configured for this route.", null, null, false);
    }

    private static LocalTime resolveDepartureGate(boolean perSlotDeparture, LocalTime hubIntake, SlaSlot sla) {
        if (perSlotDeparture) {
            return sla.cutoffTime;
        }
        if (hubIntake != null) {
            return hubIntake;
        }
        return sla.cutoffTime;
    }

    private DeliveryPromiseDTO buildScheduledPromise(
            LocalDateTime now,
            SlaSlot sla,
            String deliveryTypeUI,
            boolean shifted,
            LocalTime departureGate) {

        if (sla.deliveryTime == null) {
            return new DeliveryPromiseDTO(
                    "Delivery schedule will be confirmed at booking.", null, null, shifted);
        }

        int dayOffset = sla.deliveryDayOffset != null ? Math.max(0, sla.deliveryDayOffset) : 1;
        LocalDate handoverDate = shifted ? now.toLocalDate().plusDays(1) : now.toLocalDate();
        LocalDate deliveryDate = handoverDate.plusDays(dayOffset);

        String departureText = formatTime(departureGate);
        String deliveryTimeText = formatTime(sla.deliveryTime);

        if (shifted) {
            String deliveryDateStr = formatDate(deliveryDate);
            String handoverDateStr = formatDate(handoverDate);
            String deliveredBy = "Delivered by " + deliveryDateStr + " at " + deliveryTimeText;
            String message = "Today's dispatch slots have closed. "
                    + "Your parcel can be delivered by " + deliveryDateStr + " at " + deliveryTimeText + ".";
            String cutoffInfo = buildShiftedCutoffInfo(deliveryTypeUI, departureText, handoverDateStr);
            return new DeliveryPromiseDTO(message, cutoffInfo, deliveredBy, true);
        }

        String deliveredBy = deliveryDate.equals(now.toLocalDate().plusDays(1))
                ? "Delivered by tomorrow, " + deliveryTimeText
                : "Delivered by " + formatDate(deliveryDate) + " at " + deliveryTimeText;
        String cutoffInfo = buildActiveCutoffInfo(deliveryTypeUI, departureText);
        return new DeliveryPromiseDTO(deliveredBy, cutoffInfo, deliveredBy, false);
    }

    private DeliveryPromiseDTO buildHoursPromise(SlaSlot sla, LocalTime departureGate) {
        int h = sla.deliveredWithinHours != null ? sla.deliveredWithinHours : 0;
        String depart = formatTime(departureGate);
        String msg = h > 0
                ? "Delivered within " + h + " hour" + (h == 1 ? "" : "s") + " after " + depart + " dispatch."
                : "Delivery window will be confirmed at booking.";
        String cutoff = "Hand over before " + depart + " today.";
        return new DeliveryPromiseDTO(msg, cutoff, msg, false);
    }

    private static String buildActiveCutoffInfo(String deliveryTypeUI, String departureText) {
        if (isHubDrop(deliveryTypeUI)) {
            return "Drop your parcel at the origin hub before " + departureText + " today.";
        }
        return "Our rider will collect your parcel before " + departureText + " today.";
    }

    private static String buildShiftedCutoffInfo(
            String deliveryTypeUI, String departureText, String handoverDateStr) {
        if (isHubDrop(deliveryTypeUI)) {
            return "Drop at the origin hub before " + departureText + " on " + handoverDateStr + ".";
        }
        return "Pickup will be arranged before " + departureText + " on " + handoverDateStr + ".";
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
        final Integer deliveryDayOffset;
        final Integer deliveredWithinHours;
        final String slotLabel;

        SlaSlot(
                String deliveryType,
                LocalTime cutoffTime,
                LocalTime deliveryTime,
                Integer deliveryDayOffset,
                Integer deliveredWithinHours,
                String slotLabel) {
            this.deliveryType = deliveryType;
            this.cutoffTime = cutoffTime;
            this.deliveryTime = deliveryTime;
            this.deliveryDayOffset = deliveryDayOffset;
            this.deliveredWithinHours = deliveredWithinHours;
            this.slotLabel = slotLabel;
        }

        static SlaSlot fromZone(ZoneRouteSlaEntity e) {
            return new SlaSlot(
                    e.getDeliveryType(),
                    e.getCutoffTime(),
                    e.getDeliveryTime(),
                    1,
                    e.getDeliveredWithinHours(),
                    null);
        }

        static SlaSlot fromHub(HubRouteSlaEntity e) {
            return new SlaSlot(
                    e.getDeliveryType(),
                    e.getCutoffTime(),
                    e.getDeliveryTime(),
                    1,
                    e.getDeliveredWithinHours(),
                    null);
        }

        static SlaSlot fromHubCorridor(HubCorridorSlaEntity e) {
            return new SlaSlot(
                    e.getDeliveryType(),
                    e.getCutoffTime(),
                    e.getDeliveryTime(),
                    e.getDeliveryDayOffset(),
                    e.getDeliveredWithinHours(),
                    e.getSlotLabel());
        }
    }
}

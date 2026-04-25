package com.youdash.realtime;

import java.time.Instant;
import java.util.Set;

import com.youdash.dto.realtime.UserActiveOrderEventDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserActiveOrderTopicPublisher {
    private static final Logger log = LoggerFactory.getLogger(UserActiveOrderTopicPublisher.class);
    private static final Set<String> OUTSTATION_ONLY_STATUSES = Set.of(
            "AT_ORIGIN_HUB",
            "DEPARTED_ORIGIN_HUB",
            "AT_DESTINATION_HUB",
            "SORTED_AT_DESTINATION",
            "READY_FOR_PICKUP");

    private static final Set<String> INCITY_STATUSES = Set.of(
            "SEARCHING_RIDER",
            "RIDER_ACCEPTED",
            "PAYMENT_PENDING");


    private final SimpMessagingTemplate messagingTemplate;

    public UserActiveOrderTopicPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishSnapshot(Long userId, Long orderId, String status, Long riderId, Integer etaSeconds, Double distanceToDropKm) {
        publish(userId, "snapshot", true, orderId, status, null, riderId, etaSeconds, distanceToDropKm);
    }

    public void publishSnapshot(
            Long userId,
            Long orderId,
            String status,
            String serviceMode,
            Long riderId,
            Integer etaSeconds,
            Double distanceToDropKm) {
        publish(userId, "snapshot", true, orderId, status, serviceMode, riderId, etaSeconds, distanceToDropKm);
    }

    public void publishStatusUpdated(Long userId, Long orderId, String status, Long riderId) {
        publish(userId, "status_updated", true, orderId, status, null, riderId, null, null);
    }

    public void publishStatusUpdated(Long userId, Long orderId, String status, String serviceMode, Long riderId) {
        publish(userId, "status_updated", true, orderId, status, serviceMode, riderId, null, null);
    }

    public void publishEtaUpdated(Long userId, Long orderId, String status, Long riderId, Integer etaSeconds, Double distanceToDropKm) {
        publish(userId, "eta_updated", true, orderId, status, null, riderId, etaSeconds, distanceToDropKm);
    }

    public void publishEtaUpdated(
            Long userId,
            Long orderId,
            String status,
            String serviceMode,
            Long riderId,
            Integer etaSeconds,
            Double distanceToDropKm) {
        publish(userId, "eta_updated", true, orderId, status, serviceMode, riderId, etaSeconds, distanceToDropKm);
    }

    public void publishReleased(Long userId) {
        publish(userId, "released", false, null, null, null, null, null, null);
    }

    public void publishReleased(Long userId, Long orderId) {
        publish(userId, "released", false, orderId, null, null, null, null, null);
    }

    private void publish(
            Long userId,
            String event,
            Boolean hasActiveOrder,
            Long orderId,
            String status,
            String serviceMode,
            Long riderId,
            Integer etaSeconds,
            Double distanceToDropKm) {
        if (userId == null) {
            return;
        }
        UserActiveOrderEventDTO dto = new UserActiveOrderEventDTO();
        dto.setEvent(event);
        dto.setEventVersion(1);
        dto.setTsEpochMs(Instant.now().toEpochMilli());
        dto.setSource("backend");
        dto.setHasActiveOrder(hasActiveOrder);
        dto.setOrderId(orderId);
        dto.setStatus(status);
        dto.setServiceMode(resolveServiceMode(serviceMode, status, hasActiveOrder, orderId));
        dto.setStage(status);
        dto.setRiderId(riderId);
        dto.setEtaSeconds(etaSeconds);
        dto.setDistanceToDropKm(distanceToDropKm);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/active-order", dto);
    }

    /**
     * Guard against regressions where callers forget to pass serviceMode and the
     * client misclassifies outstation orders as incity.
     */
    private String resolveServiceMode(
            String serviceMode,
            String status,
            Boolean hasActiveOrder,
            Long orderId) {
        if (serviceMode != null && !serviceMode.isBlank()) {
            return serviceMode.trim().toUpperCase();
        }

        final String st = status == null ? "" : status.trim().toUpperCase();
        if (OUTSTATION_ONLY_STATUSES.contains(st)) {
            log.warn("active-order publish missing serviceMode for orderId={} status={}; inferred OUTSTATION", orderId, st);
            return "OUTSTATION";
        }
        if (INCITY_STATUSES.contains(st)) {
            log.warn("active-order publish missing serviceMode for orderId={} status={}; inferred INCITY", orderId, st);
            return "INCITY";
        }

        if (Boolean.TRUE.equals(hasActiveOrder)) {
            log.error("active-order publish missing serviceMode for active orderId={} status={}", orderId, st);
        }
        return null;
    }
}

package com.youdash.realtime;

import com.youdash.dto.realtime.UserActiveOrderEventDTO;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserActiveOrderTopicPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public UserActiveOrderTopicPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishSnapshot(Long userId, Long orderId, String status, Long riderId, Integer etaSeconds, Double distanceToDropKm) {
        publish(userId, "snapshot", true, orderId, status, riderId, etaSeconds, distanceToDropKm);
    }

    public void publishStatusUpdated(Long userId, Long orderId, String status, Long riderId) {
        publish(userId, "status_updated", true, orderId, status, riderId, null, null);
    }

    public void publishEtaUpdated(Long userId, Long orderId, String status, Long riderId, Integer etaSeconds, Double distanceToDropKm) {
        publish(userId, "eta_updated", true, orderId, status, riderId, etaSeconds, distanceToDropKm);
    }

    public void publishReleased(Long userId) {
        publish(userId, "released", false, null, null, null, null, null);
    }

    private void publish(
            Long userId,
            String event,
            Boolean hasActiveOrder,
            Long orderId,
            String status,
            Long riderId,
            Integer etaSeconds,
            Double distanceToDropKm) {
        if (userId == null) {
            return;
        }
        UserActiveOrderEventDTO dto = new UserActiveOrderEventDTO();
        dto.setEvent(event);
        dto.setEventVersion(1);
        dto.setHasActiveOrder(hasActiveOrder);
        dto.setOrderId(orderId);
        dto.setStatus(status);
        dto.setStage(status);
        dto.setRiderId(riderId);
        dto.setEtaSeconds(etaSeconds);
        dto.setDistanceToDropKm(distanceToDropKm);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/active-order", dto);
    }
}

package com.youdash.realtime;

import java.time.Instant;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.youdash.dto.realtime.RiderActiveOrderEventDTO;
import com.youdash.model.OrderStatus;

@Component
public class RiderActiveOrderTopicPublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public RiderActiveOrderTopicPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event) {
        publish(riderId, orderId, status, event, null, null);
    }

    /** Immediate snapshot-style push so rider UI can sync without waiting for reconnect. */
    public void publishSnapshot(Long riderId, Long orderId, OrderStatus status) {
        publish(riderId, orderId, status, "snapshot", null, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, Double collectAmount) {
        publish(riderId, orderId, status, event, null, collectAmount);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, String reason) {
        publish(riderId, orderId, status, event, reason, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, String reason, Double collectAmount) {
        if (riderId == null || orderId == null) {
            return;
        }
        RiderActiveOrderEventDTO dto = new RiderActiveOrderEventDTO();
        dto.setOrderId(orderId);
        dto.setStatus(status == null ? null : status.name());
        dto.setStage(status == null ? null : status.name());
        dto.setEvent(event);
        dto.setEventVersion(1);
        dto.setTsEpochMs(Instant.now().toEpochMilli());
        dto.setSource("backend");
        dto.setServiceMode("INCITY");
        dto.setReason(reason);
        dto.setCollectAmount(collectAmount);
        dto.setHasActiveOrder(hasActiveOrderForLifecycleEvent(event));
        dto.setNextStatus(nextStatusForEvent(status, event));
        messagingTemplate.convertAndSend("/topic/riders/" + riderId + "/active-order", dto);
    }

    /**
     * Snapshot messages set {@code hasActiveOrder} in {@link RiderActiveOrderSubscriptionListener}.
     * Lifecycle pushes set it here so clients can treat {@code hasActiveOrder} consistently.
     */
    private static Boolean hasActiveOrderForLifecycleEvent(String event) {
        if (event == null) {
            return true;
        }
        return switch (event) {
            case "released", "delivered" -> false;
            default -> true;
        };
    }

    private static String nextStatusForEvent(OrderStatus status, String event) {
        if (event != null && (event.equals("released") || event.equals("delivered"))) {
            return null;
        }
        return IncityActiveOrderNextStatus.resolve(status);
    }
}

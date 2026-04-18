package com.youdash.realtime;

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
        publish(riderId, orderId, status, event, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, String reason) {
        if (riderId == null || orderId == null) {
            return;
        }
        RiderActiveOrderEventDTO dto = new RiderActiveOrderEventDTO();
        dto.setOrderId(orderId);
        dto.setStatus(status == null ? null : status.name());
        dto.setEvent(event);
        dto.setReason(reason);
        dto.setHasActiveOrder(hasActiveOrderForLifecycleEvent(event));
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
}

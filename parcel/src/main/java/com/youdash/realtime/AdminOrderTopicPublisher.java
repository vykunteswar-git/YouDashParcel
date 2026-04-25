package com.youdash.realtime;

import java.time.Instant;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.youdash.dto.realtime.AdminOrderEventDTO;
import com.youdash.entity.OrderEntity;

@Component
public class AdminOrderTopicPublisher {
    private static final String ADMIN_ORDERS_TOPIC = "/topic/admin/orders";
    private static final int EVENT_VERSION = 1;

    private final SimpMessagingTemplate messagingTemplate;

    public AdminOrderTopicPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishOrderCreated(OrderEntity order) {
        publish("order_created", order);
    }

    public void publishStatusUpdated(OrderEntity order) {
        publish("status_updated", order);
    }

    public void publishRiderAssigned(OrderEntity order) {
        publish("rider_assigned", order);
    }

    public void publish(String eventType, OrderEntity order) {
        if (order == null || order.getId() == null) {
            return;
        }
        AdminOrderEventDTO dto = new AdminOrderEventDTO();
        dto.setEvent(eventType);
        dto.setEventType(eventType);
        dto.setEventVersion(EVENT_VERSION);
        dto.setTsEpochMs(Instant.now().toEpochMilli());
        dto.setSource("backend");
        dto.setOrderId(order.getId());
        dto.setUserId(order.getUserId());
        dto.setRiderId(order.getRiderId());
        dto.setServiceMode(order.getServiceMode() == null ? null : order.getServiceMode().name());
        dto.setStatus(order.getStatus() == null ? null : order.getStatus().name());
        dto.setPaymentType(order.getPaymentType() == null ? null : order.getPaymentType().name());
        dto.setTotalAmount(order.getTotalAmount());
        messagingTemplate.convertAndSend(ADMIN_ORDERS_TOPIC, dto);
    }
}

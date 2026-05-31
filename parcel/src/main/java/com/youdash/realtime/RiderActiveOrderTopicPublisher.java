package com.youdash.realtime;

import java.time.Instant;
import java.util.Optional;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.youdash.dto.realtime.RiderActiveOrderEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.repository.OrderRepository;
import com.youdash.util.OutstationRiderLegPolicy;

@Component
public class RiderActiveOrderTopicPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;

    public RiderActiveOrderTopicPublisher(
            SimpMessagingTemplate messagingTemplate,
            OrderRepository orderRepository) {
        this.messagingTemplate = messagingTemplate;
        this.orderRepository = orderRepository;
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event) {
        publish(riderId, orderId, status, event, null, null, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, Double collectAmount) {
        publish(riderId, orderId, status, event, null, collectAmount, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, String reason) {
        publish(riderId, orderId, status, event, reason, null, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, String reason, Double collectAmount) {
        publish(riderId, orderId, status, event, reason, collectAmount, null);
    }

    public void publish(Long riderId, Long orderId, OrderStatus status, String event, String reason, Double collectAmount,
            ServiceMode serviceMode) {
        if (riderId == null || orderId == null) {
            return;
        }
        Optional<OrderEntity> orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isPresent()
                && OutstationRiderLegPolicy.shouldSuppressRiderActiveOrderSocket(
                        orderOpt.get(), riderId, event, reason)) {
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
        dto.setServiceMode(serviceMode == null ? "INCITY" : serviceMode.name());
        dto.setReason(reason);
        dto.setCollectAmount(collectAmount);
        dto.setHasActiveOrder(hasActiveOrderForLifecycleEvent(event));
        dto.setNextStatus(nextStatusForEvent(status, event, serviceMode));
        messagingTemplate.convertAndSend("/topic/riders/" + riderId + "/active-order", dto);
    }

    public void publishSnapshot(Long riderId, Long orderId, OrderStatus status) {
        publishSnapshot(riderId, orderId, status, null);
    }

    public void publishSnapshot(Long riderId, Long orderId, OrderStatus status, ServiceMode serviceMode) {
        publish(riderId, orderId, status, "snapshot", null, null, serviceMode);
    }

    private static Boolean hasActiveOrderForLifecycleEvent(String event) {
        if (event == null) {
            return true;
        }
        return switch (event) {
            case "released", "delivered" -> false;
            default -> true;
        };
    }

    private static String nextStatusForEvent(OrderStatus status, String event, ServiceMode serviceMode) {
        if (event != null && (event.equals("released") || event.equals("delivered"))) {
            return null;
        }
        if (serviceMode == ServiceMode.OUTSTATION) {
            return OutstationActiveOrderNextStatus.resolve(status);
        }
        return IncityActiveOrderNextStatus.resolve(status);
    }
}

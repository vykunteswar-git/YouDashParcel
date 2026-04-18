package com.youdash.realtime;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import com.youdash.dto.realtime.RiderActiveOrderEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.repository.OrderRepository;

/**
 * When a rider subscribes to {@code /topic/riders/{riderId}/active-order}, publishes an immediate
 * {@code snapshot} so the app knows whether there is an active INCITY order (e.g. after reconnect)
 * without waiting for the next lifecycle event.
 */
@Component
public class RiderActiveOrderSubscriptionListener {

    private static final Pattern ACTIVE_ORDER_TOPIC =
            Pattern.compile("^/topic/riders/(\\d+)/active-order$");

    private static final List<OrderStatus> INCITY_ACTIVE_STATUSES = List.of(
            OrderStatus.RIDER_ACCEPTED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PICKED_UP,
            OrderStatus.IN_TRANSIT);

    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public RiderActiveOrderSubscriptionListener(
            OrderRepository orderRepository,
            SimpMessagingTemplate messagingTemplate) {
        this.orderRepository = orderRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String destination = acc.getDestination();
        if (destination == null) {
            return;
        }
        Matcher m = ACTIVE_ORDER_TOPIC.matcher(destination);
        if (!m.matches()) {
            return;
        }
        Long riderId = Long.valueOf(m.group(1));

        RiderActiveOrderEventDTO payload = orderRepository
                .findFirstByRiderIdAndServiceModeAndStatusInOrderByIdDesc(
                        riderId, ServiceMode.INCITY, INCITY_ACTIVE_STATUSES)
                .map(this::snapshotFromOrder)
                .orElseGet(this::snapshotIdle);

        messagingTemplate.convertAndSend(destination, payload);
    }

    private RiderActiveOrderEventDTO snapshotFromOrder(OrderEntity o) {
        RiderActiveOrderEventDTO dto = new RiderActiveOrderEventDTO();
        dto.setEvent("snapshot");
        dto.setHasActiveOrder(true);
        dto.setOrderId(o.getId());
        dto.setStatus(o.getStatus() == null ? null : o.getStatus().name());
        dto.setReason(null);
        return dto;
    }

    private RiderActiveOrderEventDTO snapshotIdle() {
        RiderActiveOrderEventDTO dto = new RiderActiveOrderEventDTO();
        dto.setEvent("snapshot");
        dto.setHasActiveOrder(false);
        dto.setOrderId(null);
        dto.setStatus(null);
        dto.setReason(null);
        return dto;
    }
}

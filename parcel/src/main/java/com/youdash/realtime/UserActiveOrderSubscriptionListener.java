package com.youdash.realtime;

import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.util.GeoUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class UserActiveOrderSubscriptionListener {

    private static final Pattern USER_ACTIVE_ORDER_TOPIC =
            Pattern.compile("^/topic/users/(\\d+)/active-order$");

    private static final List<OrderStatus> USER_ACTIVE_STATUSES = List.of(
            OrderStatus.SEARCHING_RIDER,
            OrderStatus.CREATED,
            OrderStatus.RIDER_ACCEPTED,
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.CONFIRMED,
            OrderStatus.PICKED_UP,
            OrderStatus.IN_TRANSIT);

    private final OrderRepository orderRepository;
    private final RiderRepository riderRepository;
    private final UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Value("${youdash.tracking.city-speed-kmh:20}")
    private double citySpeedKmh;

    public UserActiveOrderSubscriptionListener(
            OrderRepository orderRepository,
            RiderRepository riderRepository,
            UserActiveOrderTopicPublisher userActiveOrderTopicPublisher) {
        this.orderRepository = orderRepository;
        this.riderRepository = riderRepository;
        this.userActiveOrderTopicPublisher = userActiveOrderTopicPublisher;
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor acc = StompHeaderAccessor.wrap(event.getMessage());
        String destination = acc.getDestination();
        if (destination == null) {
            return;
        }
        Matcher m = USER_ACTIVE_ORDER_TOPIC.matcher(destination);
        if (!m.matches()) {
            return;
        }
        Long userId = Long.valueOf(m.group(1));
        orderRepository
                .findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, USER_ACTIVE_STATUSES)
                .ifPresentOrElse(this::publishSnapshotFromOrder, () -> userActiveOrderTopicPublisher.publishReleased(userId));
    }

    private void publishSnapshotFromOrder(OrderEntity order) {
        Integer etaSeconds = null;
        Double distanceToDropKm = null;
        RiderEntity rider = resolveRider(order.getRiderId());
        if (rider != null && rider.getCurrentLat() != null && rider.getCurrentLng() != null
                && order.getDropLat() != null && order.getDropLng() != null) {
            double dist = GeoUtils.haversineKm(rider.getCurrentLat(), rider.getCurrentLng(), order.getDropLat(), order.getDropLng());
            distanceToDropKm = Math.round(dist * 100.0) / 100.0;
            if (citySpeedKmh > 0) {
                etaSeconds = (int) ((dist / citySpeedKmh) * 3600);
            }
        }
        userActiveOrderTopicPublisher.publishSnapshot(
                order.getUserId(),
                order.getId(),
                order.getStatus() == null ? null : order.getStatus().name(),
                order.getRiderId(),
                etaSeconds,
                distanceToDropKm);
    }

    private RiderEntity resolveRider(Long riderId) {
        if (riderId == null) {
            return null;
        }
        return riderRepository.findById(riderId).orElse(null);
    }
}

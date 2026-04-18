package com.youdash.service.impl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.dto.realtime.RequestClosedEventDTO;
import com.youdash.dto.realtime.RiderNewOrderRequestEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.OrderDispatchEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderDispatchRepository;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.service.DispatchService;
import com.youdash.service.NotificationService;
import com.youdash.util.GeoUtils;

/**
 * In-memory dispatch suitable for a single server instance.
 * (If you scale horizontally later, move this to Redis.)
 */
@Service
public class DispatchServiceImpl implements DispatchService, DisposableBean {

    private static final int RIDER_FANOUT = 5;
    private static final long RETRY_DELAY_SECONDS = 12; // 10–15s

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dispatch-retry");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderDispatchRepository orderDispatchRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private NotificationService notificationService;

    @Override
    @Transactional
    public void dispatchNewIncityOrder(OrderEntity order) {
        if (order == null || order.getId() == null) {
            return;
        }
        if (order.getServiceMode() != ServiceMode.INCITY) {
            return;
        }
        if (order.getStatus() != OrderStatus.SEARCHING_RIDER) {
            return;
        }

        long expiryMs = order.getSearchExpiresAt() != null
                ? order.getSearchExpiresAt().toEpochMilli()
                : Instant.now().plusSeconds(30).toEpochMilli();

        sendRound(order, expiryMs, 1);

        // mandatory retry after 10–15 seconds if still not accepted.
        scheduler.schedule(() -> retryIfStillSearching(order.getId()), RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
    }

    private void retryIfStillSearching(Long orderId) {
        try {
            OrderEntity o = orderRepository.findById(orderId).orElse(null);
            if (o == null || o.getServiceMode() != ServiceMode.INCITY || o.getStatus() != OrderStatus.SEARCHING_RIDER) {
                return;
            }
            long expiryMs = o.getSearchExpiresAt() != null
                    ? o.getSearchExpiresAt().toEpochMilli()
                    : Instant.now().plusSeconds(30).toEpochMilli();
            if (System.currentTimeMillis() >= expiryMs) {
                return;
            }
            sendRound(o, expiryMs, 2);
        } catch (Exception ignored) {
            // best-effort
        }
    }

    @Transactional
    private void sendRound(OrderEntity order, long expiryMs, int round) {
        List<Long> picked = pickNearestRiders(order.getPickupLat(), order.getPickupLng(), order.getId(), RIDER_FANOUT);
        if (picked.isEmpty()) {
            return;
        }
        for (Long riderId : picked) {
            if (!orderDispatchRepository.existsByOrderIdAndRiderId(order.getId(), riderId)) {
                OrderDispatchEntity row = new OrderDispatchEntity();
                row.setOrderId(order.getId());
                row.setRiderId(riderId);
                row.setStatus("NOTIFIED");
                row.setRoundNum(round);
                orderDispatchRepository.save(row);
            }
        }

        RiderNewOrderRequestEventDTO evt = new RiderNewOrderRequestEventDTO();
        evt.setOrderId(order.getId());
        evt.setPickupLat(order.getPickupLat());
        evt.setPickupLng(order.getPickupLng());
        evt.setDropLat(order.getDropLat());
        evt.setDropLng(order.getDropLng());
        evt.setDistanceKm(order.getDistanceKm());
        // minimal earning signal: use totalAmount for now (can be replaced with commission calc later)
        evt.setEarningAmount(order.getTotalAmount());
        evt.setExpiryTimeEpochMs(expiryMs);

        for (Long riderId : picked) {
            messagingTemplate.convertAndSend("/topic/riders/" + riderId + "/new_order_request", evt);
            notificationService.sendToRider(
                    riderId,
                    "New delivery request",
                    "Order #" + order.getId() + " — open the app to accept or decline.",
                    riderNewOrderPushData(order, expiryMs),
                    NotificationType.RIDER_NEW_ORDER_REQUEST);
        }
    }

    private static Map<String, String> riderNewOrderPushData(OrderEntity order, long expiryMs) {
        Map<String, String> d = new HashMap<>(
                NotificationService.baseData(order.getId(),
                        order.getStatus() != null ? order.getStatus().name() : null,
                        NotificationType.RIDER_NEW_ORDER_REQUEST));
        d.put("expiryTimeEpochMs", String.valueOf(expiryMs));
        if (order.getPickupLat() != null) {
            d.put("pickupLat", String.valueOf(order.getPickupLat()));
        }
        if (order.getPickupLng() != null) {
            d.put("pickupLng", String.valueOf(order.getPickupLng()));
        }
        if (order.getDropLat() != null) {
            d.put("dropLat", String.valueOf(order.getDropLat()));
        }
        if (order.getDropLng() != null) {
            d.put("dropLng", String.valueOf(order.getDropLng()));
        }
        if (order.getDistanceKm() != null) {
            d.put("distanceKm", String.valueOf(order.getDistanceKm()));
        }
        if (order.getTotalAmount() != null) {
            d.put("earningAmount", String.valueOf(order.getTotalAmount()));
        }
        return d;
    }

    private List<Long> pickNearestRiders(Double pickupLat, Double pickupLng, Long orderId, int limit) {
        if (pickupLat == null || pickupLng == null) {
            return List.of();
        }
        List<Long> alreadyNotified = orderDispatchRepository.findByOrderId(orderId).stream()
                .map(OrderDispatchEntity::getRiderId)
                .collect(Collectors.toList());

        List<RiderEntity> available = riderRepository.findByIsAvailableTrue().stream()
                .filter(r -> r.getId() != null)
                .filter(r -> r.getCurrentLat() != null && r.getCurrentLng() != null)
                .filter(r -> !alreadyNotified.contains(r.getId()))
                .sorted(Comparator.comparingDouble(r -> GeoUtils.haversineKm(
                        pickupLat, pickupLng, r.getCurrentLat(), r.getCurrentLng())))
                .limit(limit)
                .collect(Collectors.toList());

        List<Long> ids = new ArrayList<>();
        for (RiderEntity r : available) {
            ids.add(r.getId());
        }
        return ids;
    }

    @Override
    @Transactional
    public void closeRequest(Long orderId, String reason, Long acceptedRiderId) {
        List<OrderDispatchEntity> rows = orderDispatchRepository.findByOrderId(orderId);
        RequestClosedEventDTO evt = new RequestClosedEventDTO();
        evt.setOrderId(orderId);
        evt.setReason(reason == null ? "closed" : reason);
        evt.setAcceptedRiderId(acceptedRiderId);

        orderDispatchRepository.updateAllStatus(orderId, "CLOSED");
        for (OrderDispatchEntity row : rows) {
            Long riderId = row.getRiderId();
            if (acceptedRiderId != null && acceptedRiderId.equals(riderId)) {
                continue;
            }
            messagingTemplate.convertAndSend("/topic/riders/" + riderId + "/request_closed", evt);
            Map<String, String> data = new HashMap<>(
                    NotificationService.baseData(orderId, null, NotificationType.RIDER_REQUEST_CLOSED));
            String reasonKey = evt.getReason() == null ? "closed" : evt.getReason();
            data.put("reason", reasonKey);
            if (acceptedRiderId != null) {
                data.put("acceptedRiderId", String.valueOf(acceptedRiderId));
            }
            String title = "Request closed";
            String body = switch (reasonKey) {
                case "accepted" -> "Another rider took order #" + orderId + ".";
                case "expired" -> "Order #" + orderId + " offer has expired.";
                case "cancelled" -> "Order #" + orderId + " was cancelled.";
                default -> "Order #" + orderId + " is no longer available.";
            };
            notificationService.sendToRider(riderId, title, body, data, NotificationType.RIDER_REQUEST_CLOSED);
        }
    }

    @Override
    public boolean wasRiderDispatched(Long orderId, Long riderId) {
        if (orderId == null || riderId == null) {
            return false;
        }
        return orderDispatchRepository.existsByOrderIdAndRiderId(orderId, riderId);
    }

    @Override
    @Transactional
    public void markRejected(Long orderId, Long riderId) {
        if (orderId == null || riderId == null) {
            return;
        }
        if (orderDispatchRepository.existsByOrderIdAndRiderId(orderId, riderId)) {
            orderDispatchRepository.updateStatus(orderId, riderId, "REJECTED");
        }
    }

    @Override
    public void destroy() {
        scheduler.shutdownNow();
    }
}


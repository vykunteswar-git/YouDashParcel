package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderTimelineEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderLocationHistoryEntity;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderLocationHistoryRepository;
import com.youdash.service.OrderTimelineService;
import com.youdash.util.GeoUtils;
import com.youdash.util.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/orders")
@Tag(name = "Tracking", description = "Live tracking APIs for customer reconnect and path replay.")
public class TrackingController {

    @Autowired
    private RiderLocationHistoryRepository locationHistoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OrderTimelineService orderTimelineService;

    @Value("${youdash.tracking.city-speed-kmh:20}")
    private double citySpeedKmh;

    @GetMapping("/{orderId}/rider-location/last")
    @Operation(summary = "Get last known rider location", description = "Returns the most recent GPS ping for this order. Use on customer reconnect instead of waiting for next WebSocket push.")
    public ApiResponse<Map<String, Object>> getLastRiderLocation(
            @PathVariable Long orderId,
            HttpServletRequest request) {

        ApiResponse<Map<String, Object>> response = new ApiResponse<>();
        try {
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            String role = resolveRole(request);
            Long actorId = resolveActorId(request);

            boolean allowed = "ADMIN".equals(role)
                    || (("USER".equals(role) && order.getUserId().equals(actorId)))
                    || (("RIDER".equals(role) && order.getRiderId() != null && order.getRiderId().equals(actorId)));

            if (!allowed) {
                throw new RuntimeException("Access denied");
            }

            RiderLocationHistoryEntity last = locationHistoryRepository
                    .findTopByOrderIdOrderByTsDesc(orderId)
                    .orElseThrow(() -> new RuntimeException("No location data yet for this order"));

            double distToDropKm = GeoUtils.haversineKm(last.getLat(), last.getLng(), order.getDropLat(),
                    order.getDropLng());
            int etaSeconds = (int) ((distToDropKm / citySpeedKmh) * 3600);

            Map<String, Object> data = new HashMap<>();
            data.put("lat", last.getLat());
            data.put("lng", last.getLng());
            data.put("ts", last.getTs().toEpochMilli());
            data.put("distanceToDropKm", Math.round(distToDropKm * 100.0) / 100.0);
            data.put("etaSeconds", etaSeconds);

            response.setData(data);
            response.setMessage("Last known location retrieved");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @GetMapping("/{orderId}/rider-location/path")
    @Operation(summary = "Get full rider path for an order", description = "Returns all GPS pings in chronological order. Use for drawing the rider's route on a map.")
    public ApiResponse<List<RiderLocationHistoryEntity>> getRiderPath(
            @PathVariable Long orderId,
            HttpServletRequest request) {

        ApiResponse<List<RiderLocationHistoryEntity>> response = new ApiResponse<>();
        try {
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            String role = resolveRole(request);
            Long actorId = resolveActorId(request);

            boolean allowed = "ADMIN".equals(role)
                    || ("USER".equals(role) && order.getUserId().equals(actorId))
                    || ("RIDER".equals(role) && order.getRiderId() != null && order.getRiderId().equals(actorId));

            if (!allowed) {
                throw new RuntimeException("Access denied");
            }

            response.setData(locationHistoryRepository.findByOrderIdOrderByTsAsc(orderId));
            response.setMessage("Path retrieved");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @GetMapping("/{orderId}/timeline")
    @Operation(summary = "Get order timeline events", description = "Returns timeline events persisted for this order in chronological order.")
    public ApiResponse<List<OrderTimelineEventDTO>> getOrderTimeline(
            @PathVariable Long orderId,
            HttpServletRequest request) {
        ApiResponse<List<OrderTimelineEventDTO>> response = new ApiResponse<>();
        try {
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            String role = resolveRole(request);
            Long actorId = resolveActorId(request);
            boolean allowed = "ADMIN".equals(role)
                    || ("USER".equals(role) && order.getUserId().equals(actorId))
                    || ("RIDER".equals(role) &&
                    ((order.getRiderId() != null && order.getRiderId().equals(actorId))
                            || (order.getPickupRiderId() != null && order.getPickupRiderId().equals(actorId))
                            || (order.getDeliveryRiderId() != null && order.getDeliveryRiderId().equals(actorId))));
            if (!allowed) {
                throw new RuntimeException("Access denied");
            }

            response.setData(orderTimelineService.timelineForOrder(orderId));
            response.setMessage("Timeline retrieved");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    private String resolveRole(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null)
            return "ANONYMOUS";
        String type = jwtUtil.extractType(token);
        return type != null ? type.toUpperCase() : "ANONYMOUS";
    }

    private Long resolveActorId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null)
            return null;
        return jwtUtil.extractId(token);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}

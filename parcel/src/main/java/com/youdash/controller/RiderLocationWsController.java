package com.youdash.controller;

import com.youdash.service.LocationUpdateRateLimiter;
import com.youdash.service.RiderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * Handles location updates sent by the rider app over the existing WebSocket
 * connection.
 * Reduces HTTP overhead vs repeated PUT /riders/me/location calls.
 *
 * Rider sends to: /app/rider/location
 * Payload: { "lat": double, "lng": double }
 */
@Controller
public class RiderLocationWsController {

    private static final String SESSION_ATTR_USER_ID = "wsUserId";
    private static final String SESSION_ATTR_TYPE = "wsTokenType";

    @Autowired
    private RiderService riderService;

    @Autowired
    private LocationUpdateRateLimiter locationRateLimiter;

    @MessageMapping("/rider/location")
    public void handleLocationUpdate(Map<String, Double> payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> session = headerAccessor.getSessionAttributes();
        if (session == null)
            return;

        Object userIdObj = session.get(SESSION_ATTR_USER_ID);
        Object typeObj = session.get(SESSION_ATTR_TYPE);

        if (!(userIdObj instanceof Long riderId) || !"RIDER".equals(typeObj)) {
            return;
        }

        Double lat = payload.get("lat");
        Double lng = payload.get("lng");
        if (lat == null || lng == null)
            return;

        if (!locationRateLimiter.tryAcquire(riderId)) {
            return;
        }

        riderService.updateLocation(riderId, lat, lng);
    }
}

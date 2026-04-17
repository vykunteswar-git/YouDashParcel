package com.youdash.security;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import com.youdash.entity.OrderEntity;
import com.youdash.repository.OrderRepository;
import com.youdash.util.JwtUtil;

/**
 * Protects subscription destinations so rider live-tracking isn't world-readable.
 *
 * Expected destinations:
 * - /topic/orders/{orderId}/rider-location
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String SESSION_ATTR_USER_ID = "wsUserId";
    private static final String SESSION_ATTR_TYPE = "wsTokenType";

    private static final Pattern ORDER_RIDER_LOCATION_TOPIC =
            Pattern.compile("^/topic/orders/(\\d+)/rider-location$");

    private static final Pattern RIDER_TOPIC =
            Pattern.compile("^/topic/riders/(\\d+)/(new_order_request|request_closed)$");

    private static final Pattern USER_EVENTS_TOPIC =
            Pattern.compile("^/topic/users/(\\d+)/order-events$");

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        StompCommand cmd = accessor.getCommand();
        if (cmd == null) {
            return message;
        }

        // Parse token once on CONNECT; reuse for later frames.
        if (StompCommand.CONNECT.equals(cmd)) {
            String token = extractBearerToken(accessor);
            if (token == null || !jwtUtil.validateToken(token)) {
                throw new RuntimeException("Unauthorized");
            }
            Long id = jwtUtil.extractId(token);
            String type = jwtUtil.extractType(token);
            Map<String, Object> session = accessor.getSessionAttributes();
            if (session != null) {
                session.put(SESSION_ATTR_USER_ID, id);
                session.put(SESSION_ATTR_TYPE, type);
            }
            return message;
        }

        // Enforce authorization on sensitive topic subscriptions.
        if (StompCommand.SUBSCRIBE.equals(cmd)) {
            String destination = accessor.getDestination();
            if (destination == null) {
                return message;
            }

            WsPrincipal p = resolvePrincipal(accessor);
            if (p == null || p.userId == null || p.tokenType == null) {
                throw new RuntimeException("Unauthorized");
            }

            Matcher riderTopic = RIDER_TOPIC.matcher(destination);
            if (riderTopic.matches()) {
                Long riderId = Long.valueOf(riderTopic.group(1));
                if ("ADMIN".equals(p.tokenType)) {
                    return message;
                }
                if ("RIDER".equals(p.tokenType) && p.userId.equals(riderId)) {
                    return message;
                }
                throw new RuntimeException("Access denied");
            }

            Matcher userEvents = USER_EVENTS_TOPIC.matcher(destination);
            if (userEvents.matches()) {
                Long userId = Long.valueOf(userEvents.group(1));
                if ("ADMIN".equals(p.tokenType)) {
                    return message;
                }
                if ("USER".equals(p.tokenType) && p.userId.equals(userId)) {
                    return message;
                }
                throw new RuntimeException("Access denied");
            }

            Matcher m = ORDER_RIDER_LOCATION_TOPIC.matcher(destination);
            if (!m.matches()) {
                return message; // other topics are not handled here
            }

            Long orderId = Long.valueOf(m.group(1));
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));

            if ("ADMIN".equals(p.tokenType)) {
                return message;
            }
            if ("USER".equals(p.tokenType) && p.userId.equals(order.getUserId())) {
                return message;
            }
            if ("RIDER".equals(p.tokenType) && order.getRiderId() != null && p.userId.equals(order.getRiderId())) {
                return message;
            }

            throw new RuntimeException("Access denied");
        }

        return message;
    }

    private WsPrincipal resolvePrincipal(StompHeaderAccessor accessor) {
        // Prefer session (from CONNECT). If missing, attempt to re-read from current frame.
        Map<String, Object> session = accessor.getSessionAttributes();
        Object uid = session == null ? null : session.get(SESSION_ATTR_USER_ID);
        Object type = session == null ? null : session.get(SESSION_ATTR_TYPE);
        if (uid instanceof Long && type instanceof String) {
            return new WsPrincipal((Long) uid, (String) type);
        }

        String token = extractBearerToken(accessor);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        return new WsPrincipal(jwtUtil.extractId(token), jwtUtil.extractType(token));
    }

    private static String extractBearerToken(StompHeaderAccessor accessor) {
        String raw = firstNativeHeader(accessor, "Authorization");
        if (raw == null) {
            raw = firstNativeHeader(accessor, "authorization");
        }
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return t.substring(7).trim();
        }
        return t;
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String key) {
        if (accessor.getNativeHeader(key) == null || accessor.getNativeHeader(key).isEmpty()) {
            return null;
        }
        return accessor.getNativeHeader(key).get(0);
    }

    private static final class WsPrincipal {
        final Long userId;
        final String tokenType;

        WsPrincipal(Long userId, String tokenType) {
            this.userId = userId;
            this.tokenType = tokenType;
        }
    }
}


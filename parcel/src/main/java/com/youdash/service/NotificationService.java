package com.youdash.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.UserEntity;
import com.youdash.notification.NotificationType;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Value("${notification.admin.fcm.tokens:}")
    private String adminFcmTokensCsv;

    /**
     * Resolve user FCM token and send (async). {@code data} values must be non-null strings for FCM data map.
     */
    /**
     * Push to rider device when {@link RiderEntity#getFcmToken()} is set.
     */
    public void sendToRider(Long riderId, String title, String body, Map<String, String> data, NotificationType type) {
        if (riderId == null) {
            return;
        }
        riderRepository.findById(riderId)
                .map(RiderEntity::getFcmToken)
                .filter(StringUtils::hasText)
                .ifPresent(token -> sendToToken(token, title, body, data, type));
    }

    public void sendToUser(Long userId, String title, String body, Map<String, String> data, NotificationType type) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(UserEntity::getFcmToken)
                .filter(StringUtils::hasText)
                .ifPresent(token -> sendToToken(token, title, body, data, type));
    }

    /**
     * Send to each configured admin device token (comma-separated in {@code notification.admin.fcm.tokens}).
     */
    public void sendToAdminDevices(String title, String body, Map<String, String> data, NotificationType type) {
        if (!StringUtils.hasText(adminFcmTokensCsv)) {
            return;
        }
        for (String raw : adminFcmTokensCsv.split(",")) {
            String token = raw == null ? "" : raw.trim();
            if (!token.isEmpty()) {
                sendToToken(token, title, body, data, type);
            }
        }
    }

    /** @deprecated Prefer {@link #sendToUser(Long, String, String, Map, NotificationType)} or {@link #sendToToken(String, String, String, Map, NotificationType)} */
    @Deprecated
    public void sendNotification(String token, String title, String body, Long orderId, NotificationType type) {
        Map<String, String> data = baseData(orderId, null, type);
        sendToToken(token, title, body, data, type);
    }

    public void sendToToken(String token, String title, String body, Map<String, String> data, NotificationType type) {
        if (!StringUtils.hasText(token)) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (firebaseMessaging == null) {
                    log.warn("FirebaseMessaging not configured. Skipping notification type={}", type);
                    return;
                }

                Map<String, String> merged = new HashMap<>();
                if (data != null) {
                    data.forEach((k, v) -> {
                        if (k != null && v != null) {
                            merged.put(k, v);
                        }
                    });
                }
                if (type != null && !merged.containsKey("type")) {
                    merged.put("type", type.name());
                }

                Message.Builder builder = Message.builder()
                        .setToken(token.trim())
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build())
                        .setAndroidConfig(AndroidConfig.builder()
                                .setPriority(AndroidConfig.Priority.HIGH)
                                .build());

                merged.forEach(builder::putData);

                String messageId = firebaseMessaging.send(builder.build());
                log.debug("FCM sent messageId={} type={}", messageId, type);
            } catch (FirebaseMessagingException e) {
                if (isTokenNotRegistered(e)) {
                    clearToken(token.trim());
                    log.warn("FCM token unregistered; cleared from DB. type={}", type);
                } else {
                    log.error("FCM send failed type={} code={} msg={}",
                            type, String.valueOf(e.getErrorCode()), e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("Unexpected notification failure type={} msg={}", type, e.getMessage(), e);
            }
        });
    }

    public static Map<String, String> baseData(Long orderId, String orderStatus, NotificationType type) {
        Map<String, String> m = new HashMap<>();
        if (orderId != null) {
            m.put("orderId", String.valueOf(orderId));
        }
        if (orderStatus != null) {
            m.put("orderStatus", orderStatus);
        }
        if (type != null) {
            m.put("type", type.name());
        }
        return m;
    }

    private boolean isTokenNotRegistered(FirebaseMessagingException e) {
        try {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return true;
            }
        } catch (Exception ignored) {
            // ignore
        }
        String msg = e.getMessage();
        return msg != null && msg.contains("registration-token-not-registered");
    }

    private void clearToken(String token) {
        try {
            userRepository.findByFcmToken(token).ifPresent(user -> {
                user.setFcmToken(null);
                userRepository.save(user);
            });
        } catch (Exception e) {
            log.warn("Failed clearing user token: {}", e.getMessage());
        }
        try {
            riderRepository.findByFcmToken(token).ifPresent(rider -> {
                rider.setFcmToken(null);
                riderRepository.save(rider);
            });
        } catch (Exception e) {
            log.warn("Failed clearing rider token: {}", e.getMessage());
        }
    }
}

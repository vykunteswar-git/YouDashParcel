package com.youdash.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class NotificationService {

    private static final AtomicBoolean LOGGED_RIDER_FCM_SKIP = new AtomicBoolean(false);
    private static final AtomicBoolean LOGGED_USER_FCM_SKIP = new AtomicBoolean(false);

    @Autowired(required = false)
    @Qualifier("riderFirebaseMessaging")
    private FirebaseMessaging riderFirebaseMessaging;

    @Autowired(required = false)
    @Qualifier("userFirebaseMessaging")
    private FirebaseMessaging userFirebaseMessaging;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private NotificationInboxService notificationInboxService;

    @Value("${notification.admin.fcm.tokens:}")
    private String adminFcmTokensCsv;

    @PostConstruct
    void logPushConfiguration() {
        if (riderFirebaseMessaging == null) {
            log.warn("FCM [rider] not configured — set firebase.rider.service-account.path to enable rider push notifications.");
        } else {
            log.info("FCM [rider] enabled.");
        }
        if (userFirebaseMessaging == null) {
            log.warn("FCM [user] not configured — set firebase.user.service-account.path to enable user push notifications.");
        } else {
            log.info("FCM [user] enabled.");
        }
    }

    public void sendToRider(Long riderId, String title, String body, Map<String, String> data, NotificationType type) {
        if (riderId == null) return;
        notificationInboxService.recordForRider(riderId, title, body, data, type);
        riderRepository.findById(riderId)
                .map(RiderEntity::getFcmToken)
                .filter(StringUtils::hasText)
                .ifPresent(token -> sendToToken(token, title, body, data, type, riderFirebaseMessaging, "rider", LOGGED_RIDER_FCM_SKIP));
    }

    public void sendToUser(Long userId, String title, String body, Map<String, String> data, NotificationType type) {
        if (userId == null) return;
        notificationInboxService.recordForUser(userId, title, body, data, type);
        userRepository.findById(userId)
                .filter(u -> Boolean.TRUE.equals(u.getActive()))
                .map(UserEntity::getFcmToken)
                .filter(StringUtils::hasText)
                .ifPresent(token -> sendToToken(token, title, body, data, type, userFirebaseMessaging, "user", LOGGED_USER_FCM_SKIP));
    }

    public void sendToAdminDevices(String title, String body, Map<String, String> data, NotificationType type) {
        if (!StringUtils.hasText(adminFcmTokensCsv)) return;
        for (String raw : adminFcmTokensCsv.split(",")) {
            String token = raw == null ? "" : raw.trim();
            if (!token.isEmpty()) {
                // Admin devices use user FCM project
                sendToToken(token, title, body, data, type, userFirebaseMessaging, "user", LOGGED_USER_FCM_SKIP);
            }
        }
    }

    /** @deprecated Prefer {@link #sendToUser} or {@link #sendToRider} */
    @Deprecated
    public void sendNotification(String token, String title, String body, Long orderId, NotificationType type) {
        Map<String, String> data = baseData(orderId, null, type);
        sendToToken(token, title, body, data, type, userFirebaseMessaging, "user", LOGGED_USER_FCM_SKIP);
    }

    public void sendToToken(String token, String title, String body, Map<String, String> data, NotificationType type) {
        sendToToken(token, title, body, data, type, userFirebaseMessaging, "user", LOGGED_USER_FCM_SKIP);
    }

    private void sendToToken(String token, String title, String body, Map<String, String> data,
            NotificationType type, FirebaseMessaging fcm, String label, AtomicBoolean skipLogged) {
        if (!StringUtils.hasText(token)) return;

        CompletableFuture.runAsync(() -> {
            try {
                if (fcm == null) {
                    if (skipLogged.compareAndSet(false, true)) {
                        log.warn("FCM [{}] send skipped — not configured. type={}", label, type);
                    } else {
                        log.debug("FCM [{}] send skipped (not configured). type={}", label, type);
                    }
                    return;
                }

                Map<String, String> merged = new HashMap<>();
                if (data != null) {
                    data.forEach((k, v) -> {
                        if (k != null && v != null) merged.put(k, v);
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

                String messageId = fcm.send(builder.build());
                log.debug("FCM [{}] sent messageId={} type={}", label, messageId, type);

            } catch (FirebaseMessagingException e) {
                if (isTokenNotRegistered(e)) {
                    clearToken(token.trim());
                    log.warn("FCM [{}] token unregistered — cleared from DB. type={}", label, type);
                } else {
                    log.error("FCM [{}] send failed type={} code={} msg={}",
                            label, type, e.getErrorCode(), e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("FCM [{}] unexpected failure type={} msg={}", label, type, e.getMessage(), e);
            }
        });
    }

    public static Map<String, String> baseData(Long orderId, String orderStatus, NotificationType type) {
        Map<String, String> m = new HashMap<>();
        if (orderId != null) m.put("orderId", String.valueOf(orderId));
        if (orderStatus != null) m.put("orderStatus", orderStatus);
        if (type != null) m.put("type", type.name());
        return m;
    }

    private boolean isTokenNotRegistered(FirebaseMessagingException e) {
        if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) return true;
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
            log.warn("Failed clearing user FCM token: {}", e.getMessage());
        }
        try {
            riderRepository.findByFcmToken(token).ifPresent(rider -> {
                rider.setFcmToken(null);
                riderRepository.save(rider);
            });
        } catch (Exception e) {
            log.warn("Failed clearing rider FCM token: {}", e.getMessage());
        }
    }
}

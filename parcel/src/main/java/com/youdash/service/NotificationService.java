package com.youdash.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import com.youdash.notification.NotificationType;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class NotificationService {

    @Autowired(required = false)
    private FirebaseMessaging firebaseMessaging;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    public void sendNotification(String token, String title, String body, Long orderId, NotificationType type) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                if (firebaseMessaging == null) {
                    log.warn("FirebaseMessaging not configured. Skipping notification type={} orderId={}", type, orderId);
                    return;
                }

                Message.Builder builder = Message.builder()
                        .setToken(token)
                        .setNotification(Notification.builder()
                                .setTitle(title)
                                .setBody(body)
                                .build());

                if (orderId != null) {
                    builder.putData("orderId", String.valueOf(orderId));
                }
                if (type != null) {
                    builder.putData("type", type.name());
                }

                String messageId = firebaseMessaging.send(builder.build());
                log.info("Notification sent messageId={} type={} orderId={}", messageId, type, orderId);
            } catch (FirebaseMessagingException e) {
                if (isTokenNotRegistered(e)) {
                    clearToken(token);
                    log.warn("FCM token unregistered; cleared from DB. type={} orderId={}", type, orderId);
                } else {
                    log.error("Failed to send notification type={} orderId={} errorCode={} message={}",
                            type, orderId, String.valueOf(e.getErrorCode()), e.getMessage(), e);
                }
            } catch (Exception e) {
                log.error("Unexpected notification failure type={} orderId={} message={}", type, orderId, e.getMessage(), e);
            }
        });
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
            log.warn("Failed clearing user token from DB: {}", e.getMessage(), e);
        }

        try {
            riderRepository.findByFcmToken(token).ifPresent(rider -> {
                rider.setFcmToken(null);
                riderRepository.save(rider);
            });
        } catch (Exception e) {
            log.warn("Failed clearing rider token from DB: {}", e.getMessage(), e);
        }
    }
}


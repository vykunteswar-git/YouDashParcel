package com.youdash.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean(name = "riderFirebaseMessaging")
    public FirebaseMessaging riderFirebaseMessaging(
            @Value("${firebase.rider.service-account.path:}") String path) {
        return initMessaging(path, "rider-app", "RiderFirebaseApp");
    }

    @Bean(name = "userFirebaseMessaging")
    public FirebaseMessaging userFirebaseMessaging(
            @Value("${firebase.user.service-account.path:}") String path) {
        return initMessaging(path, "user-app", "UserFirebaseApp");
    }

    private FirebaseMessaging initMessaging(String path, String label, String appName) {
        if (path == null || path.trim().isEmpty()) {
            log.warn("FCM [{}] disabled — firebase.{}.service-account.path not set.", label, label.replace("-app", ""));
            return null;
        }
        Path file = Path.of(path.trim());
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            log.warn("FCM [{}] disabled — service account file not found at: {}", label, path);
            return null;
        }
        try {
            // Reuse existing app if already initialized (e.g. hot reload)
            FirebaseApp app;
            if (FirebaseApp.getApps().stream().anyMatch(a -> a.getName().equals(appName))) {
                app = FirebaseApp.getInstance(appName);
            } else {
                try (InputStream is = new FileInputStream(file.toFile())) {
                    FirebaseOptions options = FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(is))
                            .build();
                    app = FirebaseApp.initializeApp(options, appName);
                }
            }
            log.info("FCM [{}] initialized — app: {}", label, appName);
            return FirebaseMessaging.getInstance(app);
        } catch (Exception e) {
            log.error("FCM [{}] failed to initialize — pushes disabled. Reason: {}", label, e.getMessage(), e);
            return null;
        }
    }
}

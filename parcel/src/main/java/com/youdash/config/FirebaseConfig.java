package com.youdash.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;

import java.io.FileInputStream;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    @Conditional(FirebaseCredentialsPresentCondition.class)
    public FirebaseApp firebaseApp(@Value("${firebase.service-account.path}") String serviceAccountPath) {
        try {
            if (FirebaseApp.getApps() != null && !FirebaseApp.getApps().isEmpty()) {
                return FirebaseApp.getInstance();
            }

            try (InputStream serviceAccount = new FileInputStream(serviceAccountPath)) {
                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .build();
                log.info("Initializing FirebaseApp using service account at path: {}", serviceAccountPath);
                return FirebaseApp.initializeApp(options);
            }
        } catch (Exception e) {
            log.error("Failed to initialize FirebaseApp. Notifications will be disabled. Reason: {}", e.getMessage(),
                    e);
            throw new IllegalStateException("Failed to initialize FirebaseApp", e);
        }
    }

    @Bean
    @Conditional(FirebaseCredentialsPresentCondition.class)
    public FirebaseMessaging firebaseMessaging(FirebaseApp firebaseApp) {
        return FirebaseMessaging.getInstance(firebaseApp);
    }
}

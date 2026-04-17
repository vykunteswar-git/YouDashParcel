package com.youdash.config;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Enables Firebase beans only when a non-empty service account path is
 * configured
 * and the file exists on disk.
 */
public class FirebaseCredentialsPresentCondition implements Condition, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Environment env = environment != null ? environment : context.getEnvironment();
        String p = env.getProperty("firebase.service-account.path");
        if (p == null || p.trim().isEmpty()) {
            return false;
        }
        try {
            Path path = Path.of(p.trim());
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception ignored) {
            return false;
        }
    }
}

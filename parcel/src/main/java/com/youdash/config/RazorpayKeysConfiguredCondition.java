package com.youdash.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.util.Locale;

/**
 * True when the active mode (test/live) has both key id and secret set.
 */
public class RazorpayKeysConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        var env = context.getEnvironment();
        String rawMode = env.getProperty("razorpay.mode", "test");
        String mode = rawMode == null ? "test" : rawMode.trim().toLowerCase(Locale.ROOT);
        boolean live = "live".equals(mode);

        String keyId = live
                ? env.getProperty("razorpay.live.key_id", "")
                : env.getProperty("razorpay.test.key_id", "");
        String keySecret = live
                ? env.getProperty("razorpay.live.key_secret", "")
                : env.getProperty("razorpay.test.key_secret", "");

        return notBlank(keyId) && notBlank(keySecret);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}

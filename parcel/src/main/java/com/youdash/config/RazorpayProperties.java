package com.youdash.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

@Getter
@Setter
@ConfigurationProperties(prefix = "razorpay")
public class RazorpayProperties {
    private String mode = "test";
    private KeyPair live = new KeyPair();
    private KeyPair test = new KeyPair();
    private String webhookSecret;

    @Getter
    @Setter
    public static class KeyPair {
        private String keyId;
        private String keySecret;
    }

    public String getActiveKeyId() {
        return isLiveMode() ? live.getKeyId() : test.getKeyId();
    }

    public String getActiveKeySecret() {
        return isLiveMode() ? live.getKeySecret() : test.getKeySecret();
    }

    public String getNormalizedMode() {
        String raw = mode == null ? "test" : mode.trim().toLowerCase(Locale.ROOT);
        return "live".equals(raw) ? "live" : "test";
    }

    public boolean isLiveMode() {
        return "live".equals(getNormalizedMode());
    }
}

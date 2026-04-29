package com.youdash.config;

import com.razorpay.RazorpayClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayConfig {

    @Bean
    public RazorpayClient razorpayClient(RazorpayProperties razorpayProperties) throws Exception {
        String keyId = razorpayProperties.getActiveKeyId();
        String keySecret = razorpayProperties.getActiveKeySecret();
        if (isBlank(keyId) || isBlank(keySecret)) {
            throw new IllegalStateException(
                    "Razorpay active keys not configured for mode="
                            + razorpayProperties.getNormalizedMode()
                            + ". Set env vars for the selected mode.");
        }
        return new RazorpayClient(keyId, keySecret);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

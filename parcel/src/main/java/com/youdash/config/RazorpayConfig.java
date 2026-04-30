package com.youdash.config;

import com.razorpay.RazorpayClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class RazorpayConfig {

    @Bean
    @Conditional(RazorpayKeysConfiguredCondition.class)
    public RazorpayClient razorpayClient(RazorpayProperties razorpayProperties) throws Exception {
        return new RazorpayClient(
                razorpayProperties.getActiveKeyId(),
                razorpayProperties.getActiveKeySecret());
    }
}

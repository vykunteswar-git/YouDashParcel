package com.youdash.controller;

import com.youdash.config.RazorpayProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payment")
@CrossOrigin(origins = "*")
public class PaymentConfigController {

    private final RazorpayProperties razorpayProperties;

    public PaymentConfigController(RazorpayProperties razorpayProperties) {
        this.razorpayProperties = razorpayProperties;
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok(Map.of(
                "mode", razorpayProperties.getNormalizedMode(),
                "keyId", safe(razorpayProperties.getActiveKeyId())));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}

package com.youdash.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.service.PaymentService;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create-order")
    public ApiResponse<Map<String, Object>> createRazorpayOrder(@RequestBody Map<String, Double> request) {
        Double amount = request.get("amount");
        if (amount == null) {
            throw new RuntimeException("Amount is required");
        }
        if (amount <= 0) {
            throw new RuntimeException("Invalid amount");
        }
        return paymentService.createRazorpayOrder(amount);
    }

    @PostMapping("/success")
    public ApiResponse<String> handlePaymentSuccess(@RequestBody Map<String, Long> request) {
        Long orderId = request.get("orderId");
        if (orderId == null) {
            throw new RuntimeException("OrderId is required");
        }
        return paymentService.handlePaymentSuccess(orderId);
    }
}

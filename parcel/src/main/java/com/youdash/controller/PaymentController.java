package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayCreateOrderRequestDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.dto.RazorpayVerifyRequestDTO;
import com.youdash.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Razorpay checkout and webhooks")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Operation(summary = "Create Razorpay order for an internal order (ONLINE only)")
    @PostMapping("/create-order")
    public ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(
            @RequestBody RazorpayCreateOrderRequestDTO dto,
            @RequestAttribute("userId") Long userId) {
        return paymentService.createRazorpayOrder(dto.getOrderId(), userId);
    }

    @Operation(summary = "Verify Razorpay payment after checkout success")
    @PostMapping("/verify")
    public ApiResponse<OrderResponseDTO> verifyPayment(
            @RequestBody RazorpayVerifyRequestDTO dto,
            @RequestAttribute("userId") Long userId) {
        return paymentService.verifyPayment(dto, userId);
    }

    @Operation(summary = "Razorpay webhook (no JWT; uses X-Razorpay-Signature)")
    @PostMapping("/webhook")
    public ApiResponse<String> webhook(HttpServletRequest request, @RequestBody String body) {
        String sig = request.getHeader("X-Razorpay-Signature");
        return paymentService.handleRazorpayWebhook(body, sig);
    }
}

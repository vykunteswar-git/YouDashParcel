package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.CollectPaymentRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayCreateOrderRequestDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.dto.RazorpayVerifyRequestDTO;
import com.youdash.dto.UpiQrCreatedDTO;
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

    @Operation(summary = "Rider marks COD as collected (CASH or QR manual) before completing delivery. Requires RIDER token.")
    @PostMapping("/collect")
    public ApiResponse<String> collectPayment(
            @RequestBody CollectPaymentRequestDTO dto,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "type", required = false) String tokenType) {
        if (!"RIDER".equals(tokenType)) {
            ApiResponse<String> err = new ApiResponse<>();
            err.setMessage("Rider token required");
            err.setMessageKey("ERROR");
            err.setStatus(403);
            err.setSuccess(false);
            return err;
        }
        return paymentService.collectCodPayment(userId, dto);
    }

    @Operation(summary = "Create Razorpay UPI QR for COD collection. Requires RIDER token.")
    @PostMapping("/qr")
    public ApiResponse<UpiQrCreatedDTO> createUpiQr(
            @RequestBody java.util.Map<String, Object> body,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "type", required = false) String tokenType) {
        if (!"RIDER".equals(tokenType)) {
            ApiResponse<UpiQrCreatedDTO> err = new ApiResponse<>();
            err.setMessage("Rider token required");
            err.setMessageKey("ERROR");
            err.setStatus(403);
            err.setSuccess(false);
            return err;
        }
        Object raw = body.get("orderId");
        Long orderId = raw == null ? null : Long.parseLong(raw.toString());
        return paymentService.createUpiQr(userId, orderId);
    }
}

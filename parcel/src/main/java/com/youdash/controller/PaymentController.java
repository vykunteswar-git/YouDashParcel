package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayCreateOrderRequestDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.dto.RazorpayVerifyPaymentRequestDTO;
import com.youdash.service.PaymentService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/payments")
@Tag(name = "Payments", description = "Razorpay payment APIs")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping(value = "/create-order", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Create Razorpay order",
            description = "Creates a Razorpay order for an existing internal orderId (amount is taken from DB).",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RazorpayCreateOrderRequestDTO.class)
                    )
            )
    )
    public ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(@RequestBody RazorpayCreateOrderRequestDTO request) {
        if (request == null || request.getOrderId() == null || request.getOrderId().isBlank()) {
            throw new RuntimeException("orderId is required");
        }
        return paymentService.createRazorpayOrder(request.getOrderId().trim());
    }

    @PostMapping(value = "/verify", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Verify Razorpay payment",
            description = "Verifies Razorpay signature and marks internal order as PAID. Returns full order in data (paymentStatus, etc.). On verification failure, data may still contain the current order snapshot when the order was found.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = RazorpayVerifyPaymentRequestDTO.class)
                    )
            )
    )
    public ApiResponse<OrderResponseDTO> verifyPayment(@RequestBody RazorpayVerifyPaymentRequestDTO request) {
        if (request == null || request.getOrderId() == null || request.getOrderId().isBlank()) {
            throw new RuntimeException("orderId is required");
        }
        if (request.getRazorpayOrderId() == null) throw new RuntimeException("razorpayOrderId is required");
        if (request.getRazorpayPaymentId() == null) throw new RuntimeException("razorpayPaymentId is required");
        if (request.getRazorpaySignature() == null) throw new RuntimeException("razorpaySignature is required");

        return paymentService.verifyPayment(
                request.getOrderId().trim(),
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId(),
                request.getRazorpaySignature()
        );
    }

    @PostMapping("/webhook")
    @Operation(summary = "Razorpay webhook", description = "Webhook endpoint for Razorpay events (payment.captured / payment.failed).")
    public ApiResponse<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String razorpaySignature
    ) {
        return paymentService.handleRazorpayWebhook(payload, razorpaySignature);
    }
}

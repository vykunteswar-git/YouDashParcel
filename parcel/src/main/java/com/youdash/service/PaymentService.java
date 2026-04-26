package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.CollectPaymentRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.dto.RazorpayVerifyRequestDTO;
import com.youdash.dto.UpiQrCreatedDTO;

public interface PaymentService {

    ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(String orderIdOrReference, Long userId);

    ApiResponse<OrderResponseDTO> verifyPayment(RazorpayVerifyRequestDTO dto, Long userId);

    ApiResponse<String> handleRazorpayWebhook(String payload, String razorpaySignature);

    /** Rider marks COD as collected (before completing delivery). */
    ApiResponse<String> collectCodPayment(Long riderId, CollectPaymentRequestDTO dto);

    /** Create a Razorpay UPI QR for the order's COD amount. */
    ApiResponse<UpiQrCreatedDTO> createUpiQr(Long riderId, Long orderId);
}

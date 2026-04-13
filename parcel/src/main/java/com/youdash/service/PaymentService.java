package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.dto.RazorpayVerifyRequestDTO;

public interface PaymentService {

    ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(String orderIdOrReference, Long userId);

    ApiResponse<OrderResponseDTO> verifyPayment(RazorpayVerifyRequestDTO dto, Long userId);

    ApiResponse<String> handleRazorpayWebhook(String payload, String razorpaySignature);
}

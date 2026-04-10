package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;

public interface PaymentService {

    ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(String orderIdOrReference);

    ApiResponse<OrderResponseDTO> verifyPayment(String orderIdOrReference, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature);

    ApiResponse<String> handleRazorpayWebhook(String payload, String razorpaySignature);

}

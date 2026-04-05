package com.youdash.service;

import java.util.Map;

import com.youdash.bean.ApiResponse;

public interface PaymentService {

    ApiResponse<Map<String, Object>> createRazorpayOrder(Double amount);

    ApiResponse<String> handlePaymentSuccess(Long orderId);

}

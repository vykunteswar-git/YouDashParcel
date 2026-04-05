package com.youdash.service.impl;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.youdash.bean.ApiResponse;
import com.youdash.entity.OrderEntity;
import com.youdash.repository.OrderRepository;
import com.youdash.service.PaymentService;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${razorpay.key_id}")
    private String keyId;

    @Value("${razorpay.key_secret}")
    private String keySecret;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public ApiResponse<Map<String, Object>> createRazorpayOrder(Double amount) {
        ApiResponse<Map<String, Object>> response = new ApiResponse<>();
        try {
            if (amount == null) {
                throw new RuntimeException("Amount is required");
            }
            if (amount <= 0) {
                throw new RuntimeException("Invalid amount");
            }

            RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", (int) (amount * 100)); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_" + System.currentTimeMillis());

            Order order = razorpayClient.orders.create(orderRequest);

            Map<String, Object> orderDetails = new HashMap<>();
            orderDetails.put("id", order.get("id"));
            orderDetails.put("amount", order.get("amount"));
            orderDetails.put("currency", order.get("currency"));

            response.setData(orderDetails);
            response.setMessage("Razorpay order created successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage("Failed to create Razorpay order: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<String> handlePaymentSuccess(Long orderId) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (orderId == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

            if ("PAID".equals(order.getPaymentStatus())) {
                throw new RuntimeException("Order already paid");
            }

            order.setPaymentStatus("PAID");
            order.setStatus("ASSIGNED");
            orderRepository.save(order);

            response.setData("Payment successful and order updated");
            response.setMessage("Payment processed successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }
}

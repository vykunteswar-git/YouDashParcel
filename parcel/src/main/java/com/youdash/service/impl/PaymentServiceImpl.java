package com.youdash.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.repository.OrderRepository;
import com.youdash.service.OrderService;
import com.youdash.service.PaymentService;

@Service
public class PaymentServiceImpl implements PaymentService {

    @Value("${razorpay.key_id}")
    private String keyId;

    @Value("${razorpay.key_secret}")
    private String keySecret;

    @Value("${razorpay.webhook_secret:}")
    private String webhookSecret;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Override
    public ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(String orderIdOrReference) {
        ApiResponse<RazorpayOrderCreatedDTO> response = new ApiResponse<>();
        try {
            OrderEntity orderEntity = resolveOrderByIdOrReference(orderIdOrReference);
            Long internalId = orderEntity.getId();

            if ("PAID".equalsIgnoreCase(orderEntity.getPaymentStatus())) {
                throw new RuntimeException("Order already paid");
            }

            Double amount = orderEntity.getTotalAmount();
            if (amount == null || amount <= 0) {
                throw new RuntimeException("Order totalAmount is invalid");
            }

            RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            long amountPaise = Math.round(amount * 100);
            orderRequest.put("amount", amountPaise); // paise
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "order_" + internalId);
            JSONObject notes = new JSONObject();
            notes.put("orderId", String.valueOf(internalId));
            if (orderEntity.getUserId() != null) {
                notes.put("userId", String.valueOf(orderEntity.getUserId()));
            }
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);

            String razorpayOrderId = order.get("id");
            orderEntity.setRazorpayOrderId(razorpayOrderId);
            orderEntity.setPaymentMethod("RAZORPAY");
            if (orderEntity.getPaymentCreatedAt() == null) {
                orderEntity.setPaymentCreatedAt(LocalDateTime.now());
            }
            orderEntity.setPaymentUpdatedAt(LocalDateTime.now());
            orderRepository.save(orderEntity);

            RazorpayOrderCreatedDTO dto = new RazorpayOrderCreatedDTO();
            dto.setRazorpayOrderId(razorpayOrderId);
            Object amt = order.get("amount");
            dto.setAmount(amt == null ? null : ((Number) amt).longValue());
            Object cur = order.get("currency");
            dto.setCurrency(cur == null ? null : cur.toString());
            dto.setKeyId(keyId);

            response.setData(dto);
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
    public ApiResponse<OrderResponseDTO> verifyPayment(String orderIdOrReference, String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity resolvedOrder = null;
        try {
            resolvedOrder = resolveOrderByIdOrReference(orderIdOrReference);
            OrderEntity order = resolvedOrder;

            if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                response.setMessage("Payment already verified");
                response.setMessageKey("SUCCESS");
                response.setStatus(200);
                response.setSuccess(true);
                attachOrderDto(response, order.getId());
                return response;
            }

            if (razorpayOrderId == null || razorpayOrderId.trim().isEmpty()) {
                throw new RuntimeException("razorpayOrderId is required");
            }
            if (razorpayPaymentId == null || razorpayPaymentId.trim().isEmpty()) {
                throw new RuntimeException("razorpayPaymentId is required");
            }
            if (razorpaySignature == null || razorpaySignature.trim().isEmpty()) {
                throw new RuntimeException("razorpaySignature is required");
            }

            if (order.getRazorpayOrderId() == null || !order.getRazorpayOrderId().equals(razorpayOrderId)) {
                throw new RuntimeException("Razorpay orderId mismatch for this order");
            }

            boolean ok = verifyRazorpaySignature(razorpayOrderId, razorpayPaymentId, razorpaySignature, keySecret);
            if (!ok) {
                throw new RuntimeException("Invalid Razorpay signature");
            }

            // Optional: validate amount by fetching Razorpay order
            try {
                RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);
                Order rpOrder = razorpayClient.orders.fetch(razorpayOrderId);
                long rpAmount = ((Number) rpOrder.get("amount")).longValue();
                long expectedAmount = Math.round((order.getTotalAmount() == null ? 0.0 : order.getTotalAmount()) * 100);
                if (expectedAmount <= 0) {
                    throw new RuntimeException("Order totalAmount is invalid");
                }
                if (rpAmount != expectedAmount) {
                    throw new RuntimeException("Amount mismatch");
                }
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception ignored) {
                // If Razorpay fetch fails, don't block verify (signature already proves authenticity)
            }

            order.setPaymentStatus("PAID");
            order.setRazorpayPaymentId(razorpayPaymentId);
            order.setPaymentMethod("RAZORPAY");
            if (order.getPaymentCreatedAt() == null) {
                order.setPaymentCreatedAt(LocalDateTime.now());
            }
            order.setPaymentUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);

            response.setMessage("Payment verified successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            attachOrderDto(response, order.getId());

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
            if (resolvedOrder != null) {
                attachOrderDto(response, resolvedOrder.getId());
            }
        }
        return response;
    }

    private void attachOrderDto(ApiResponse<OrderResponseDTO> response, Long internalOrderId) {
        ApiResponse<OrderResponseDTO> fetched = orderService.getOrderById(internalOrderId);
        if (Boolean.TRUE.equals(fetched.getSuccess()) && fetched.getData() != null) {
            response.setData(fetched.getData());
        }
    }

    @Override
    public ApiResponse<String> handleRazorpayWebhook(String payload, String razorpaySignature) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
                throw new RuntimeException("Webhook secret not configured");
            }
            if (payload == null) throw new RuntimeException("payload is required");
            if (razorpaySignature == null || razorpaySignature.trim().isEmpty()) {
                throw new RuntimeException("X-Razorpay-Signature is required");
            }

            boolean ok = verifyWebhookSignature(payload, razorpaySignature, webhookSecret);
            if (!ok) {
                throw new RuntimeException("Invalid webhook signature");
            }

            JSONObject json = new JSONObject(payload);
            String event = json.optString("event", "");
            JSONObject paymentEntity = json
                    .optJSONObject("payload")
                    .optJSONObject("payment")
                    .optJSONObject("entity");

            String razorpayOrderId = paymentEntity == null ? null : paymentEntity.optString("order_id", null);
            String razorpayPaymentId = paymentEntity == null ? null : paymentEntity.optString("id", null);

            if (razorpayOrderId == null || razorpayOrderId.trim().isEmpty()) {
                throw new RuntimeException("Webhook missing order_id");
            }

            OrderEntity order = orderRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new RuntimeException("Order not found for razorpayOrderId: " + razorpayOrderId));

            if ("payment.captured".equalsIgnoreCase(event)) {
                if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                    order.setPaymentStatus("PAID");
                    if (razorpayPaymentId != null && !razorpayPaymentId.trim().isEmpty()) {
                        order.setRazorpayPaymentId(razorpayPaymentId);
                    }
                    order.setPaymentMethod("RAZORPAY");
                    if (order.getPaymentCreatedAt() == null) {
                        order.setPaymentCreatedAt(LocalDateTime.now());
                    }
                    order.setPaymentUpdatedAt(LocalDateTime.now());
                    orderRepository.save(order);
                }
            } else if ("payment.failed".equalsIgnoreCase(event)) {
                if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                    order.setPaymentStatus("FAILED");
                    order.setPaymentMethod("RAZORPAY");
                    if (order.getPaymentCreatedAt() == null) {
                        order.setPaymentCreatedAt(LocalDateTime.now());
                    }
                    order.setPaymentUpdatedAt(LocalDateTime.now());
                    orderRepository.save(order);
                }
            }

            response.setData("OK");
            response.setMessage("Webhook processed");
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

    private OrderEntity resolveOrderByIdOrReference(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new RuntimeException("orderId is required");
        }
        String s = raw.trim();
        if (s.regionMatches(true, 0, "YP-", 0, 3)) {
            return orderRepository.findByOrderId(s)
                    .orElseThrow(() -> new RuntimeException("Order not found with order_id: " + s));
        }
        try {
            long id = Long.parseLong(s);
            return orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid orderId: " + s);
        }
    }

    private static boolean verifyRazorpaySignature(
            String razorpayOrderId,
            String razorpayPaymentId,
            String razorpaySignature,
            String secret
    ) {
        try {
            String payload = razorpayOrderId + "|" + razorpayPaymentId;
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = toHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    razorpaySignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = toHex(hash);
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.UTF_8),
                    signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }
}

package com.youdash.service.impl;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RazorpayOrderCreatedDTO;
import com.youdash.dto.RazorpayVerifyRequestDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.dto.realtime.UserOrderEventDTO;
import com.youdash.realtime.RiderActiveOrderTopicPublisher;
import com.youdash.realtime.UserActiveOrderTopicPublisher;
import com.youdash.service.NotificationDedupService;
import com.youdash.service.NotificationService;
import com.youdash.service.OrderTimelineService;
import com.youdash.service.OrderService;
import com.youdash.service.PaymentService;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.List;

@Service
public class PaymentServiceImpl implements PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

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

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDedupService notificationDedupService;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RiderActiveOrderTopicPublisher riderActiveOrderTopicPublisher;

    @Autowired
    private UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Autowired
    private OrderTimelineService orderTimelineService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<RazorpayOrderCreatedDTO> createRazorpayOrder(String orderIdOrReference, Long userId) {
        ApiResponse<RazorpayOrderCreatedDTO> response = new ApiResponse<>();
        try {
            OrderEntity orderEntity = resolveOrderByIdOrReference(orderIdOrReference);
            assertOrderOwner(orderEntity, userId);

            if (orderEntity.getPaymentType() != PaymentType.ONLINE) {
                throw new RuntimeException("Razorpay is only available for ONLINE orders");
            }
            if ("PAID".equalsIgnoreCase(orderEntity.getPaymentStatus())) {
                throw new RuntimeException("Order already paid");
            }

            // INCITY new flow: only allow payment initiation after rider accepted, within due time.
            if (orderEntity.getServiceMode() == ServiceMode.INCITY) {
                if (orderEntity.getStatus() != OrderStatus.RIDER_ACCEPTED) {
                    throw new RuntimeException("Payment can be initiated only after rider accepts the order");
                }
                if (orderEntity.getPaymentDueAt() != null && orderEntity.getPaymentDueAt().isBefore(Instant.now())) {
                    throw new RuntimeException("Payment window expired");
                }
            }

            Double amount = orderEntity.getTotalAmount();
            if (amount == null || amount <= 0) {
                throw new RuntimeException("Order totalAmount is invalid");
            }

            RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            long amountPaise = Math.round(amount * 100);
            orderRequest.put("amount", amountPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "order_" + orderEntity.getId());
            JSONObject notes = new JSONObject();
            notes.put("orderId", String.valueOf(orderEntity.getId()));
            if (orderEntity.getDisplayOrderId() != null) {
                notes.put("displayOrderId", orderEntity.getDisplayOrderId());
            }
            if (orderEntity.getUserId() != null) {
                notes.put("userId", String.valueOf(orderEntity.getUserId()));
            }
            orderRequest.put("notes", notes);

            Order order = razorpayClient.orders.create(orderRequest);

            String razorpayOrderId = order.get("id");
            orderEntity.setRazorpayOrderId(razorpayOrderId);
            orderEntity.setPaymentMethod("RAZORPAY");
            Instant now = Instant.now();
            if (orderEntity.getPaymentCreatedAt() == null) {
                orderEntity.setPaymentCreatedAt(now);
            }
            orderEntity.setPaymentUpdatedAt(now);
            orderRepository.save(orderEntity);

            // status transition: RIDER_ACCEPTED -> PAYMENT_PENDING (idempotent with conditional update)
            if (orderEntity.getServiceMode() == ServiceMode.INCITY) {
                if (orderEntity.getPaymentType() == PaymentType.COD) {
                    throw new RuntimeException("Razorpay is not applicable for COD orders");
                }
                orderRepository.tryMarkPaymentPending(
                        orderEntity.getId(),
                        OrderStatus.RIDER_ACCEPTED,
                        OrderStatus.PAYMENT_PENDING,
                        Instant.now());
            }

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
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> verifyPayment(RazorpayVerifyRequestDTO dto, Long userId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity resolvedOrder = null;
        try {
            // Use one reference time for the full verification flow to avoid
            // boundary races (window expires mid-request after payment success).
            Instant verificationStartedAt = Instant.now();
            if (dto == null || dto.getOrderId() == null || dto.getOrderId().isBlank()) {
                throw new RuntimeException("orderId is required");
            }
            resolvedOrder = resolveOrderByIdOrReference(dto.getOrderId());
            OrderEntity order = resolvedOrder;
            assertOrderOwner(order, userId);

            if ("PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                response.setMessage("Payment already verified");
                response.setMessageKey("SUCCESS");
                response.setStatus(200);
                response.setSuccess(true);
                attachOrderDto(response, order);
                return response;
            }

            if (order.getServiceMode() == ServiceMode.INCITY) {
                if (!(order.getStatus() == OrderStatus.PAYMENT_PENDING || order.getStatus() == OrderStatus.RIDER_ACCEPTED)) {
                    throw new RuntimeException("Payment cannot be verified from status: " + order.getStatus());
                }
            }

            if (dto.getRazorpayOrderId() == null || dto.getRazorpayOrderId().trim().isEmpty()) {
                throw new RuntimeException("razorpayOrderId is required");
            }
            if (dto.getRazorpayPaymentId() == null || dto.getRazorpayPaymentId().trim().isEmpty()) {
                throw new RuntimeException("razorpayPaymentId is required");
            }
            if (dto.getRazorpaySignature() == null || dto.getRazorpaySignature().trim().isEmpty()) {
                throw new RuntimeException("razorpaySignature is required");
            }

            if (order.getRazorpayOrderId() == null || !order.getRazorpayOrderId().equals(dto.getRazorpayOrderId())) {
                throw new RuntimeException("Razorpay orderId mismatch for this order");
            }

            if (!verifyRazorpaySignature(dto.getRazorpayOrderId(), dto.getRazorpayPaymentId(), dto.getRazorpaySignature(), keySecret)) {
                throw new RuntimeException("Invalid Razorpay signature");
            }

            try {
                RazorpayClient razorpayClient = new RazorpayClient(keyId, keySecret);
                Order rpOrder = razorpayClient.orders.fetch(dto.getRazorpayOrderId());
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
                // Signature already verified; optional amount check
            }

            if (order.getServiceMode() == ServiceMode.INCITY) {
                int updated = orderRepository.markPaidAndConfirm(
                        order.getId(),
                        ServiceMode.INCITY,
                        List.of(OrderStatus.RIDER_ACCEPTED, OrderStatus.PAYMENT_PENDING),
                        OrderStatus.CONFIRMED,
                        "PAID",
                        dto.getRazorpayPaymentId().trim(),
                        "RAZORPAY",
                        verificationStartedAt);
                log.info(
                        "PAY_VERIFY_FINALIZE orderId={} updated={} statusBefore={} paymentStatusBefore={} dueAt={}",
                        order.getId(),
                        updated,
                        order.getStatus(),
                        order.getPaymentStatus(),
                        order.getPaymentDueAt());
                // If updated==0 it could be already PAID/confirmed/cancelled; refetch to confirm state.
                order = orderRepository.findById(order.getId()).orElse(order);
                if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())
                        && (order.getStatus() == OrderStatus.RIDER_ACCEPTED || order.getStatus() == OrderStatus.PAYMENT_PENDING)) {
                    // Fallback path: if conditional bulk update misses due to race/timing,
                    // finalize directly when order is still in a verifiable state.
                    Instant now = Instant.now();
                    order.setPaymentStatus("PAID");
                    order.setStatus(OrderStatus.CONFIRMED);
                    order.setRazorpayPaymentId(dto.getRazorpayPaymentId().trim());
                    order.setPaymentMethod("RAZORPAY");
                    if (order.getPaymentCreatedAt() == null) {
                        order.setPaymentCreatedAt(now);
                    }
                    order.setPaymentUpdatedAt(now);
                    order = orderRepository.save(order);
                    log.warn(
                            "PAY_VERIFY_FALLBACK_APPLIED orderId={} statusAfter={} paymentStatusAfter={}",
                            order.getId(),
                            order.getStatus(),
                            order.getPaymentStatus());
                }
                if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                    log.warn(
                            "PAY_VERIFY_FINALIZE_FAILED orderId={} statusAfter={} paymentStatusAfter={} dueAt={}",
                            order.getId(),
                            order.getStatus(),
                            order.getPaymentStatus(),
                            order.getPaymentDueAt());
                    throw new RuntimeException("Payment verification failed to finalize order");
                }
                if (order.getRiderId() != null) {
                    riderRepository.reserveIfAvailable(order.getRiderId());
                }
                sendConfirmedEvent(order.getUserId(), order.getId());
                OrderEntity forRider = orderRepository.findById(order.getId()).orElse(order);
                if (forRider.getRiderId() != null && forRider.getStatus() == OrderStatus.CONFIRMED) {
                    riderActiveOrderTopicPublisher.publish(
                            forRider.getRiderId(), forRider.getId(), OrderStatus.CONFIRMED, "confirmed");
                }
                orderTimelineService.appendEvent(
                        order,
                        OrderStatus.CONFIRMED,
                        "payment_verified",
                        order.getOriginHubId(),
                        order.getRiderId(),
                        null,
                        "Payment verified and order confirmed");
            } else {
                Instant now = Instant.now();
                order.setPaymentStatus("PAID");
                order.setRazorpayPaymentId(dto.getRazorpayPaymentId());
                order.setPaymentMethod("RAZORPAY");
                if (order.getPaymentCreatedAt() == null) {
                    order.setPaymentCreatedAt(now);
                }
                order.setPaymentUpdatedAt(now);
                orderRepository.save(order);
                orderTimelineService.appendEvent(
                        order,
                        order.getStatus(),
                        "payment_verified",
                        order.getOriginHubId(),
                        order.getRiderId(),
                        null,
                        "Outstation payment verified");
            }
            notifyPaymentSuccess(order);

            response.setMessage("Payment verified successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            attachOrderDto(response, order);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
            if (resolvedOrder != null) {
                attachOrderDto(response, resolvedOrder);
            }
        }
        return response;
    }

    private void sendConfirmedEvent(Long userId, Long orderId) {
        if (userId == null || orderId == null) {
            return;
        }
        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(orderId);
        evt.setEvent("confirmed");
        evt.setEventType("confirmed");
        evt.setEventVersion(1);
        evt.setStatus(OrderStatus.CONFIRMED.name());
        evt.setStage(OrderStatus.CONFIRMED.name());
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", evt);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                userId, orderId, OrderStatus.CONFIRMED.name(), null);
    }

    @Override
    public ApiResponse<String> handleRazorpayWebhook(String payload, String razorpaySignature) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (webhookSecret == null || webhookSecret.trim().isEmpty()) {
                throw new RuntimeException("Webhook secret not configured");
            }
            if (payload == null) {
                throw new RuntimeException("payload is required");
            }
            if (razorpaySignature == null || razorpaySignature.trim().isEmpty()) {
                throw new RuntimeException("X-Razorpay-Signature is required");
            }

            if (!verifyWebhookSignature(payload, razorpaySignature, webhookSecret)) {
                throw new RuntimeException("Invalid webhook signature");
            }

            JSONObject json = new JSONObject(payload);
            String event = json.optString("event", "");
            JSONObject payloadObj = json.optJSONObject("payload");
            if (payloadObj == null) {
                throw new RuntimeException("Webhook missing payload");
            }
            JSONObject paymentWrap = payloadObj.optJSONObject("payment");
            if (paymentWrap == null) {
                throw new RuntimeException("Webhook missing payment");
            }
            JSONObject paymentEntity = paymentWrap.optJSONObject("entity");

            String razorpayOrderId = paymentEntity == null ? null : paymentEntity.optString("order_id", null);
            String razorpayPaymentId = paymentEntity == null ? null : paymentEntity.optString("id", null);

            if (razorpayOrderId == null || razorpayOrderId.trim().isEmpty()) {
                throw new RuntimeException("Webhook missing order_id");
            }

            OrderEntity order = orderRepository.findByRazorpayOrderId(razorpayOrderId)
                    .orElseThrow(() -> new RuntimeException("Order not found for razorpayOrderId: " + razorpayOrderId));

            Instant now = Instant.now();
            if ("payment.captured".equalsIgnoreCase(event)) {
                if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                    order.setPaymentStatus("PAID");
                    if (razorpayPaymentId != null && !razorpayPaymentId.trim().isEmpty()) {
                        order.setRazorpayPaymentId(razorpayPaymentId);
                    }
                    order.setPaymentMethod("RAZORPAY");
                    if (order.getPaymentCreatedAt() == null) {
                        order.setPaymentCreatedAt(now);
                    }
                    order.setPaymentUpdatedAt(now);
                    orderRepository.save(order);
                    notifyPaymentSuccess(order);
                }
            } else if ("payment.failed".equalsIgnoreCase(event)) {
                if (!"PAID".equalsIgnoreCase(order.getPaymentStatus())) {
                    order.setPaymentStatus("FAILED");
                    order.setPaymentMethod("RAZORPAY");
                    if (order.getPaymentCreatedAt() == null) {
                        order.setPaymentCreatedAt(now);
                    }
                    order.setPaymentUpdatedAt(now);
                    orderRepository.save(order);
                    notifyPaymentFailed(order);
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

    private void assertOrderOwner(OrderEntity order, Long userId) {
        if (userId == null || !Objects.equals(order.getUserId(), userId)) {
            throw new RuntimeException("Access denied");
        }
    }

    private void attachOrderDto(ApiResponse<OrderResponseDTO> response, OrderEntity order) {
        ApiResponse<OrderResponseDTO> fetched = orderService.getOrder(order.getId(), order.getUserId(), null, true);
        if (Boolean.TRUE.equals(fetched.getSuccess()) && fetched.getData() != null) {
            response.setData(fetched.getData());
        }
    }

    private void notifyPaymentSuccess(OrderEntity order) {
        if (order == null || order.getId() == null || order.getUserId() == null) {
            return;
        }
        if (!notificationDedupService.tryAcquire("payment-success:" + order.getId())) {
            return;
        }
        String ref = order.getDisplayOrderId() != null ? order.getDisplayOrderId() : "#" + order.getId();
        notificationService.sendToUser(
                order.getUserId(),
                "Payment successful",
                "Payment received for order " + ref + ".",
                NotificationService.baseData(order.getId(), order.getPaymentStatus(), NotificationType.PAYMENT_SUCCESS),
                NotificationType.PAYMENT_SUCCESS);
        notificationService.sendToAdminDevices(
                "Payment successful",
                "Order " + ref + " was paid successfully.",
                NotificationService.baseData(order.getId(), order.getPaymentStatus(), NotificationType.ADMIN_PAYMENT_SUCCESS),
                NotificationType.ADMIN_PAYMENT_SUCCESS);
        if (order.getRiderId() != null && "PAID".equalsIgnoreCase(order.getPaymentStatus())) {
            Map<String, String> riderData = new HashMap<>(
                    NotificationService.baseData(
                            order.getId(),
                            order.getStatus() != null ? order.getStatus().name() : null,
                            NotificationType.RIDER_ORDER_PAYMENT_CONFIRMED));
            riderData.put("riderId", String.valueOf(order.getRiderId()));
            notificationService.sendToRider(
                    order.getRiderId(),
                    "Payment received",
                    "Customer paid for order " + ref + ". You can proceed with pickup.",
                    riderData,
                    NotificationType.RIDER_ORDER_PAYMENT_CONFIRMED);
        }
    }

    private void notifyPaymentFailed(OrderEntity order) {
        if (order == null || order.getId() == null || order.getUserId() == null) {
            return;
        }
        if (!notificationDedupService.tryAcquire("payment-failed:" + order.getId())) {
            return;
        }
        String ref = order.getDisplayOrderId() != null ? order.getDisplayOrderId() : "#" + order.getId();
        notificationService.sendToUser(
                order.getUserId(),
                "Payment failed",
                "Payment failed for order " + ref + ". Please try again.",
                NotificationService.baseData(order.getId(), order.getPaymentStatus(), NotificationType.PAYMENT_FAILED),
                NotificationType.PAYMENT_FAILED);
        notificationService.sendToAdminDevices(
                "Payment failed",
                "Order " + ref + " payment failed.",
                NotificationService.baseData(order.getId(), order.getPaymentStatus(), NotificationType.ADMIN_PAYMENT_FAILED),
                NotificationType.ADMIN_PAYMENT_FAILED);
    }

    private OrderEntity resolveOrderByIdOrReference(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new RuntimeException("orderId is required");
        }
        String s = raw.trim();
        if (s.regionMatches(true, 0, "YP-", 0, 3)) {
            return orderRepository.findByDisplayOrderId(s)
                    .orElseThrow(() -> new RuntimeException("Order not found with display id: " + s));
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
            return secureEqualHex(expected, razorpaySignature);
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

    private static boolean secureEqualHex(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        String a = expected.trim().toLowerCase(Locale.ROOT);
        String b = actual.trim().toLowerCase(Locale.ROOT);
        if (a.length() != b.length()) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hash = sha256Hmac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = toHex(hash);
            return secureEqualHex(expected, signature);
        } catch (Exception e) {
            return false;
        }
    }
}

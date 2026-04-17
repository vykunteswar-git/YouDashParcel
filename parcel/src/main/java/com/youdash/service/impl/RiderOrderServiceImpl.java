package com.youdash.service.impl;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.realtime.UserOrderEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.service.DispatchService;
import com.youdash.service.RiderOrderService;

@Service
public class RiderOrderServiceImpl implements RiderOrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private OrderServiceImpl orderServiceImpl; // reuse toOrderDto without duplicating mapping

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> accept(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (riderId == null || orderId == null) {
            response.setMessage("riderId and orderId are required");
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
            return response;
        }

        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (order.getServiceMode() != ServiceMode.INCITY) {
            throw new RuntimeException("Only INCITY orders can be accepted by rider");
        }
        if (order.getStatus() != OrderStatus.SEARCHING_RIDER) {
            throw new RuntimeException("Order not available for acceptance");
        }
        if (order.getSearchExpiresAt() != null && order.getSearchExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Order request expired");
        }
        if (!dispatchService.wasRiderDispatched(orderId, riderId)) {
            throw new RuntimeException("Rider was not dispatched for this order");
        }

        // 1) Lock rider immediately (reserve).
        int reserved = riderRepository.reserveIfAvailable(riderId);
        if (reserved == 0) {
            throw new RuntimeException("Rider is not available");
        }

        // 2) Concurrency-safe order accept.
        Instant now = Instant.now();
        Instant paymentDue = now.plusSeconds(60);
        int updated = orderRepository.tryAcceptOrder(
                orderId,
                riderId,
                ServiceMode.INCITY,
                OrderStatus.SEARCHING_RIDER,
                OrderStatus.RIDER_ACCEPTED,
                now,
                paymentDue,
                "UNPAID",
                now);
        if (updated == 0) {
            // someone else accepted / expired; release rider
            riderRepository.release(riderId);
            throw new RuntimeException("Order already accepted or expired");
        }

        // 3) Close request for other riders.
        dispatchService.closeRequest(orderId, "accepted", riderId);

        // 4) Notify user immediately: rider_found + payment_required.
        Long userId = order.getUserId();
        sendUserEvent(userId, orderId, "rider_found", OrderStatus.RIDER_ACCEPTED, paymentDue, riderId);
        sendUserEvent(userId, orderId, "payment_required", OrderStatus.RIDER_ACCEPTED, paymentDue, riderId);

        // return updated order DTO
        OrderEntity refreshed = orderRepository.findById(orderId).orElse(order);
        response.setData(orderServiceImpl.toOrderDto(refreshed));
        response.setMessage("Order accepted");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    @Override
    public ApiResponse<String> reject(Long riderId, Long orderId) {
        ApiResponse<String> response = new ApiResponse<>();
        if (riderId == null || orderId == null) {
            response.setMessage("riderId and orderId are required");
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
            return response;
        }
        if (!dispatchService.wasRiderDispatched(orderId, riderId)) {
            throw new RuntimeException("Rider was not dispatched for this order");
        }
        dispatchService.markRejected(orderId, riderId);
        response.setData("Rejected");
        response.setMessage("Rejected");
        response.setMessageKey("SUCCESS");
        response.setStatus(200);
        response.setSuccess(true);
        return response;
    }

    private void sendUserEvent(Long userId, Long orderId, String event, OrderStatus status, Instant paymentDueAt,
            Long riderId) {
        if (userId == null) {
            return;
        }
        UserOrderEventDTO dto = new UserOrderEventDTO();
        dto.setOrderId(orderId);
        dto.setEvent(event);
        dto.setStatus(status == null ? null : status.name());
        dto.setPaymentDueAtEpochMs(paymentDueAt == null ? null : paymentDueAt.toEpochMilli());
        dto.setRiderId(riderId);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", dto);
    }

    @SuppressWarnings("unused")
    private RiderEntity requireRider(Long riderId) {
        return riderRepository.findById(riderId).orElseThrow(() -> new RuntimeException("Rider not found"));
    }
}

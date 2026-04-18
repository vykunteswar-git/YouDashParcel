package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;

public interface RiderOrderService {
    ApiResponse<OrderResponseDTO> accept(Long riderId, Long orderId);

    ApiResponse<String> reject(Long riderId, Long orderId);

    ApiResponse<OrderResponseDTO> markPickedUp(Long riderId, Long orderId);

    ApiResponse<OrderResponseDTO> startTransit(Long riderId, Long orderId);

    /** No status change; emits a user socket event for UI/logging. */
    ApiResponse<OrderResponseDTO> reachDestination(Long riderId, Long orderId);
}


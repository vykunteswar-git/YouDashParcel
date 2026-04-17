package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;

public interface RiderOrderService {
    ApiResponse<OrderResponseDTO> accept(Long riderId, Long orderId);
    ApiResponse<String> reject(Long riderId, Long orderId);
}


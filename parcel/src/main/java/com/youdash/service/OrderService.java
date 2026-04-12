package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.model.OrderStatus;

import java.util.List;

public interface OrderService {

    ApiResponse<FinalPriceResponseDTO> calculateFinal(FinalPriceRequestDTO dto);

    ApiResponse<OrderResponseDTO> createOrder(Long userId, CreateOrderRequestDTO dto);

    ApiResponse<OrderResponseDTO> getOrder(Long orderId, Long tokenUserId, boolean admin);

    ApiResponse<List<OrderResponseDTO>> listUserOrders(Long userId, Long tokenUserId, boolean admin);

    ApiResponse<ManualOrderRequestResponseDTO> manualRequest(Long userId, ManualOrderRequestDTO dto);

    ApiResponse<List<OrderResponseDTO>> listAllOrdersAdmin();

    ApiResponse<OrderResponseDTO> adminAssignRider(Long orderId, Long riderId);

    ApiResponse<OrderResponseDTO> adminUpdateStatus(Long orderId, OrderStatus status);
}

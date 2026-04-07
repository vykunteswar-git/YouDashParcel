package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;

public interface OrderService {

    ApiResponse<OrderResponseDTO> createOrder(OrderRequestDTO dto);

    ApiResponse<List<OrderResponseDTO>> getOrdersByUserId(Long userId);

    ApiResponse<OrderResponseDTO> getOrderById(Long id);

    ApiResponse<OrderResponseDTO> updateOrderStatus(Long id, String status);

    ApiResponse<OrderResponseDTO> assignRider(Long id, Long riderId);

    ApiResponse<OrderResponseDTO> cancelOrder(Long id);

    ApiResponse<OrderResponseDTO> updateOrder(Long id, OrderRequestDTO dto);

}

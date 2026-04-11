package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.OrderTrackingDTO;

public interface OrderService {

    ApiResponse<OrderResponseDTO> createOrder(OrderRequestDTO dto);

    ApiResponse<List<OrderResponseDTO>> getOrdersByUserId(Long userId);

    ApiResponse<OrderResponseDTO> getOrderById(Long id);

    ApiResponse<OrderTrackingDTO> getOrderTracking(Long id);

    ApiResponse<OrderResponseDTO> updateOrderStatus(Long id, String status);

    ApiResponse<OrderResponseDTO> assignRider(Long id, Long riderId);

    ApiResponse<OrderResponseDTO> cancelOrder(Long id);

    ApiResponse<OrderResponseDTO> updateOrder(Long id, OrderRequestDTO dto);

    ApiResponse<List<OrderResponseDTO>> listUnassignedOrders();

    ApiResponse<OrderResponseDTO> updateHubStatus(Long orderId, String status);

    ApiResponse<OrderResponseDTO> completeHubDelivery(Long orderId);

    ApiResponse<OrderResponseDTO> assignDeliveryRider(Long orderId, Long riderId);

    ApiResponse<OrderResponseDTO> markReadyForDelivery(Long orderId);

    ApiResponse<List<OrderResponseDTO>> listOrdersForRider(Long riderId);

    ApiResponse<OrderResponseDTO> riderAcceptOrder(Long riderId, Long orderId);

    ApiResponse<OrderResponseDTO> riderRejectOrder(Long riderId, Long orderId);

    ApiResponse<OrderResponseDTO> riderUpdateOrderStatus(Long riderId, Long orderId, String status);

}

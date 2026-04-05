package com.youdash.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.repository.OrderRepository;
import com.youdash.service.OrderService;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public ApiResponse<OrderResponseDTO> createOrder(OrderRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (dto.getUserId() == null) {
                throw new RuntimeException("UserId is required");
            }

            OrderEntity order = new OrderEntity();
            
            // Manual Mapping from DTO to Entity
            order.setUserId(dto.getUserId());
            order.setPickupAddress(dto.getPickupAddress());
            order.setDeliveryAddress(dto.getDeliveryAddress());
            order.setReceiverName(dto.getReceiverName());
            order.setReceiverPhone(dto.getReceiverPhone());
            order.setCategory(dto.getCategory());
            order.setDescription(dto.getDescription());
            order.setWeight(dto.getWeight());
            order.setImageUrl(dto.getImageUrl());
            if (order.getImageUrl() == null || order.getImageUrl().isEmpty()) {
                throw new RuntimeException("Image URL is required");
            }

            order.setVehicleType(dto.getVehicleType());
            order.setDistanceKm(dto.getDistanceKm());
            order.setTotalAmount(dto.getTotalAmount());
            order.setPaymentType(dto.getPaymentType());
            order.setScheduledDate(dto.getScheduledDate());
            order.setTimeSlot(dto.getTimeSlot());
            
            // Default values
            order.setOrderId("YP-" + (System.currentTimeMillis() % 1000000000));
            order.setStatus("CREATED");
            order.setPaymentStatus("PENDING");

            OrderEntity savedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(savedOrder));
            response.setMessage("Order created successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage("Order creation failed: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<OrderResponseDTO>> getOrdersByUserId(Long userId) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            List<OrderEntity> orders = orderRepository.findByUserId(userId);
            List<OrderResponseDTO> dtos = orders.stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());

            response.setData(dtos);
            response.setMessage("Orders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage("Failed to fetch orders: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> getOrderById(Long id) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            response.setData(mapToResponseDTO(order));
            response.setMessage("Order fetched successfully");
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

    @Override
    public ApiResponse<OrderResponseDTO> updateOrderStatus(Long id, String status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            // Status Validation
            List<String> allowedStatuses = List.of("CREATED", "ASSIGNED", "PICKED", "IN_TRANSIT", "DELIVERED", "CANCELLED");
            if (!allowedStatuses.contains(status)) {
                throw new RuntimeException("Invalid status");
            }

            order.setStatus(status);
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Order status updated successfully");
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

    @Override
    public ApiResponse<OrderResponseDTO> assignRider(Long id, Long riderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            order.setRiderId(riderId);
            order.setStatus("ASSIGNED");
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Rider assigned successfully");
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

    @Override
    public ApiResponse<OrderResponseDTO> cancelOrder(Long id) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            order.setStatus("CANCELLED");
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Order cancelled successfully");
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

    // Helper map method
    private OrderResponseDTO mapToResponseDTO(OrderEntity order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setOrderId(order.getOrderId());
        dto.setStatus(order.getStatus());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setImageUrl(order.getImageUrl());
        return dto;
    }
}

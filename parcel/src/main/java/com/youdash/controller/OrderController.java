package com.youdash.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.service.OrderService;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ApiResponse<OrderResponseDTO> createOrder(@RequestBody OrderRequestDTO dto) {
        return orderService.createOrder(dto);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderResponseDTO>> getOrdersByUserId(@PathVariable Long userId) {
        return orderService.getOrdersByUserId(userId);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponseDTO> getOrderById(@PathVariable Long id) {
        return orderService.getOrderById(id);
    }

    @PutMapping("/{id}/status")
    public ApiResponse<OrderResponseDTO> updateOrderStatus(
            @PathVariable Long id, 
            @RequestBody Map<String, String> statusMap) {
        String status = statusMap.get("status");
        return orderService.updateOrderStatus(id, status);
    }

    @PostMapping("/{id}/assign-rider")
    public ApiResponse<OrderResponseDTO> assignRider(
            @PathVariable Long id, 
            @RequestBody Map<String, Object> riderMap) {
        
        Object riderIdObj = riderMap.get("riderId");
        if (riderIdObj == null) {
            throw new RuntimeException("riderId is required");
        }
        
        Long riderId = Long.valueOf(riderIdObj.toString());
        return orderService.assignRider(id, riderId);
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<OrderResponseDTO> cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }

    @PutMapping("/{id}/update")
    public ApiResponse<OrderResponseDTO> updateOrder(
            @PathVariable Long id, 
            @RequestBody OrderRequestDTO dto) {
        return orderService.updateOrder(id, dto);
    }
}

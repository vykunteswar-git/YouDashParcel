package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.OrderTrackingDTO;
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

    @GetMapping("/{id}/tracking")
    public ApiResponse<OrderTrackingDTO> getOrderTracking(@PathVariable Long id) {
        return orderService.getOrderTracking(id);
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<OrderResponseDTO> cancelOrder(@PathVariable Long id) {
        return orderService.cancelOrder(id);
    }
}

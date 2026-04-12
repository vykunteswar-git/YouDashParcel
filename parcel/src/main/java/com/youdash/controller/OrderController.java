package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.service.OrderService;
import com.youdash.service.QuoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private OrderService orderService;

    @PostMapping("/quote")
    public ApiResponse<QuoteResponseDTO> quote(@RequestBody QuoteRequestDTO dto) {
        return quoteService.quote(dto);
    }

    @PostMapping("/calculate-final")
    public ApiResponse<FinalPriceResponseDTO> calculateFinal(@RequestBody FinalPriceRequestDTO dto) {
        return orderService.calculateFinal(dto);
    }

    @PostMapping
    public ApiResponse<OrderResponseDTO> createOrder(
            @RequestBody CreateOrderRequestDTO dto,
            @RequestAttribute("userId") Long userId) {
        return orderService.createOrder(userId, dto);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<OrderResponseDTO>> listForUser(
            @PathVariable Long userId,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        boolean admin = "ADMIN".equals(type);
        return orderService.listUserOrders(userId, tokenUserId, admin);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponseDTO> getOrder(
            @PathVariable Long id,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        boolean admin = "ADMIN".equals(type);
        return orderService.getOrder(id, tokenUserId, admin);
    }

    @PostMapping("/manual-request")
    public ApiResponse<ManualOrderRequestResponseDTO> manualRequest(
            @RequestBody ManualOrderRequestDTO dto,
            @RequestAttribute("userId") Long userId) {
        return orderService.manualRequest(userId, dto);
    }
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.service.OrderService;
import com.youdash.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders — User", description = "User order lifecycle: quote, create order, fetch, cancel. INCITY uses rider-accept + payment window.")
public class OrderController {

    @Autowired
    private QuoteService quoteService;

    @Autowired
    private OrderService orderService;

    @PostMapping("/quote")
    @Operation(summary = "Get price quote (INCITY/OUTSTATION)")
    public ApiResponse<QuoteResponseDTO> quote(@RequestBody QuoteRequestDTO dto) {
        return quoteService.quote(dto);
    }

    @PostMapping("/calculate-final")
    @Operation(summary = "Calculate outstation final price (hub-based)")
    public ApiResponse<FinalPriceResponseDTO> calculateFinal(@RequestBody FinalPriceRequestDTO dto) {
        return orderService.calculateFinal(dto);
    }

    @PostMapping
    @Operation(summary = "Create order", description = "INCITY: status=SEARCHING_RIDER, dispatches to nearby riders via socket. OUTSTATION: status=CREATED, admin assigns rider.")
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
    @Operation(summary = "Get order by id")
    public ApiResponse<OrderResponseDTO> getOrder(
            @PathVariable Long id,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        boolean admin = "ADMIN".equals(type);
        return orderService.getOrder(id, tokenUserId, type, admin);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel order (INCITY only)", description = "Cancels an active INCITY order, releases reserved rider if any, closes rider request.")
    public ApiResponse<OrderResponseDTO> cancel(
            @PathVariable Long id,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        return orderService.cancelOrder(id, tokenUserId, type);
    }

    @PostMapping("/{id}/verify-otp")
    @Operation(
            summary = "Verify delivery OTP (USER or assigned RIDER, IN_TRANSIT)",
            description = "Call while the order is IN_TRANSIT. After success, the assigned rider marks delivery with POST /order/complete "
                    + "(ONLINE: body orderId only; COD: include cod collection fields on complete).")
    public ApiResponse<OrderResponseDTO> verifyDeliveryOtp(
            @PathVariable Long id,
            @RequestBody VerifyDeliveryOtpRequestDTO dto,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        return orderService.verifyDeliveryOtp(id, dto, tokenUserId, type);
    }

    @PostMapping("/manual-request")
    public ApiResponse<ManualOrderRequestResponseDTO> manualRequest(
            @RequestBody ManualOrderRequestDTO dto,
            @RequestAttribute("userId") Long userId) {
        return orderService.manualRequest(userId, dto);
    }
}

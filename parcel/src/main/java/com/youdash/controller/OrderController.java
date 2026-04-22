package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.dto.rating.RiderRatingRequestDTO;
import com.youdash.service.OrderService;
import com.youdash.service.QuoteService;
import com.youdash.service.RiderRatingService;
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

    @Autowired
    private RiderRatingService riderRatingService;

    @PostMapping("/quote")
    @Operation(summary = "Get price quote (INCITY/OUTSTATION)")
    public ApiResponse<QuoteResponseDTO> quote(@RequestBody QuoteRequestDTO dto) {
        return quoteService.quote(dto);
    }

    @PostMapping("/calculate-final")
    @Operation(summary = "Calculate outstation final price (hub-based)")
    public ApiResponse<FinalPriceResponseDTO> calculateFinal(
            @RequestBody FinalPriceRequestDTO dto,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "type", required = false) String type) {
        if (!"USER".equals(type)) {
            ApiResponse<FinalPriceResponseDTO> denied = new ApiResponse<>();
            denied.setMessage("User token required for price preview with coupons");
            denied.setMessageKey("ERROR");
            denied.setSuccess(false);
            denied.setStatus(403);
            return denied;
        }
        return orderService.calculateFinal(userId, dto);
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

    @GetMapping("/user/{userId}/address-suggestions")
    @Operation(
            summary = "Recent pickup/drop locations for autofill",
            description =
                    "Deduped coordinates from past orders (newest first). Use role PICKUP → sender + pickupLat/Lng; DROP → receiver + dropLat/Lng on POST /orders.")
    public ApiResponse<List<OrderAddressSuggestionDTO>> addressSuggestions(
            @PathVariable Long userId,
            @RequestParam(required = false) Integer limit,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        boolean admin = "ADMIN".equals(type);
        return orderService.listUserOrderAddressSuggestions(userId, tokenUserId, admin, limit);
    }

    @PostMapping("/user/{userId}/address-suggestions/edit")
    @Operation(summary = "Edit address suggestion details", description = "Updates manual fields (address/tag/doorNo/landmark/contact) for a role+lat/lng suggestion.")
    public ApiResponse<String> editAddressSuggestion(
            @PathVariable Long userId,
            @RequestBody OrderAddressSuggestionEditRequestDTO dto,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        boolean admin = "ADMIN".equals(type);
        return orderService.editUserOrderAddressSuggestion(userId, tokenUserId, admin, dto);
    }

    @PostMapping("/user/{userId}/address-suggestions/hide")
    @Operation(summary = "Hide address suggestion", description = "Hides a role+lat/lng suggestion from the recent list without changing order history.")
    public ApiResponse<String> hideAddressSuggestion(
            @PathVariable Long userId,
            @RequestBody OrderAddressSuggestionHideRequestDTO dto,
            @RequestAttribute("userId") Long tokenUserId,
            @RequestAttribute(value = "type", required = false) String type) {
        boolean admin = "ADMIN".equals(type);
        return orderService.hideUserOrderAddressSuggestion(userId, tokenUserId, admin, dto);
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

    @PostMapping("/{id}/rate-rider")
    @Operation(summary = "Rate rider for delivered order", description = "USER only. One rating per order.")
    public ApiResponse<String> rateRider(
            @PathVariable Long id,
            @RequestBody RiderRatingRequestDTO dto,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "type", required = false) String type) {
        if (!"USER".equals(type)) {
            ApiResponse<String> denied = new ApiResponse<>();
            denied.setMessage("User token required");
            denied.setMessageKey("ERROR");
            denied.setSuccess(false);
            denied.setStatus(403);
            return denied;
        }
        return riderRatingService.submitUserRating(id, userId, dto);
    }

    @PostMapping("/manual-request")
    public ApiResponse<ManualOrderRequestResponseDTO> manualRequest(
            @RequestBody ManualOrderRequestDTO dto,
            @RequestAttribute("userId") Long userId) {
        return orderService.manualRequest(userId, dto);
    }
}

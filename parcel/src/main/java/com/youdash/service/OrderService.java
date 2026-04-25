package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.dto.wallet.OrderCompleteRequestDTO;
import com.youdash.model.OrderStatus;

import java.util.List;

public interface OrderService {

    ApiResponse<FinalPriceResponseDTO> calculateFinal(Long userId, FinalPriceRequestDTO dto);

    ApiResponse<OrderResponseDTO> createOrder(Long userId, CreateOrderRequestDTO dto);

    ApiResponse<OrderResponseDTO> getOrder(Long orderId, Long tokenUserId, String tokenType, boolean admin);

    ApiResponse<List<OrderResponseDTO>> listUserOrders(Long userId, Long tokenUserId, boolean admin);

    /**
     * Distinct recent pickup/drop coordinates from the user's orders (for create-order autofill).
     *
     * @param limit max suggestions to return (capped server-side)
     */
    ApiResponse<List<OrderAddressSuggestionDTO>> listUserOrderAddressSuggestions(
            Long userId, Long tokenUserId, boolean admin, Integer limit);

    ApiResponse<String> editUserOrderAddressSuggestion(
            Long userId, Long tokenUserId, boolean admin, OrderAddressSuggestionEditRequestDTO dto);

    ApiResponse<String> hideUserOrderAddressSuggestion(
            Long userId, Long tokenUserId, boolean admin, OrderAddressSuggestionHideRequestDTO dto);

    ApiResponse<List<OrderResponseDTO>> listRiderOrders(Long riderId);

    ApiResponse<ManualOrderRequestResponseDTO> manualRequest(Long userId, ManualOrderRequestDTO dto);

    ApiResponse<List<OrderResponseDTO>> listAllOrdersAdmin();

    ApiResponse<OrderResponseDTO> adminAssignRider(Long orderId, Long riderId);

    ApiResponse<OrderResponseDTO> adminAssignRiders(
            Long orderId,
            Long pickupRiderId,
            Long deliveryRiderId);

    ApiResponse<OrderResponseDTO> adminUpdateStatus(Long orderId, OrderStatus status);

    ApiResponse<OrderResponseDTO> completeOrderForRider(Long tokenUserId, String tokenType, OrderCompleteRequestDTO dto);

    ApiResponse<OrderResponseDTO> cancelOrder(Long orderId, Long tokenUserId, String tokenType);

    ApiResponse<OrderResponseDTO> verifyDeliveryOtp(Long orderId, VerifyDeliveryOtpRequestDTO dto, Long tokenUserId, String tokenType);

    ApiResponse<OrderResponseDTO> resendDeliveryOtp(Long orderId, Long tokenUserId, String tokenType);
}

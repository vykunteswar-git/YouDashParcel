package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.wallet.OrderCompleteRequestDTO;
import com.youdash.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/order")
@Tag(name = "Rider — Delivery", description = "INCITY: complete delivery after OTP verification. Rider JWT (type=RIDER) required.")
public class OrderCompletionController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/complete")
    @Operation(
            summary = "Complete delivery (IN_TRANSIT → DELIVERED)",
            description = "Requires RIDER token. Order must be IN_TRANSIT and delivery OTP must already be verified "
                    + "(POST /orders/{id}/verify-otp). "
                    + "ONLINE: send only orderId (numeric id or YP- reference). "
                    + "COD: send codCollectionMode (CASH or QR). Amount is auto-captured from order total.")
    public ApiResponse<OrderResponseDTO> complete(
            @RequestBody OrderCompleteRequestDTO dto,
            @RequestAttribute("userId") Long riderUserId,
            @RequestAttribute(value = "type", required = false) String tokenType) {
        return orderService.completeOrderForRider(riderUserId, tokenType, dto);
    }
}

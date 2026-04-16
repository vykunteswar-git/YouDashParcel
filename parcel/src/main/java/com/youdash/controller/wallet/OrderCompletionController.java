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

@RestController
@RequestMapping("/order")
public class OrderCompletionController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/complete")
    public ApiResponse<OrderResponseDTO> complete(
            @RequestBody OrderCompleteRequestDTO dto,
            @RequestAttribute("userId") Long riderUserId,
            @RequestAttribute(value = "type", required = false) String tokenType) {
        return orderService.completeOrderForRider(riderUserId, tokenType, dto);
    }
}

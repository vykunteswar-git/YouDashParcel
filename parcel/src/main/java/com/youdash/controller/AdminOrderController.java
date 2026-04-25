package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminOrderAssignDTO;
import com.youdash.dto.AdminOrderStatusDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.model.OrderStatus;
import com.youdash.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping
    public ApiResponse<List<OrderResponseDTO>> listAll() {
        return orderService.listAllOrdersAdmin();
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponseDTO> getOne(@PathVariable Long id) {
        return orderService.getOrder(id, null, null, true);
    }

    @PostMapping("/{id}/assign-rider")
    public ApiResponse<OrderResponseDTO> assignRider(
            @PathVariable Long id,
            @RequestBody AdminOrderAssignDTO dto) {
        final Long pickupRiderId = dto.getPickupRiderId() != null ? dto.getPickupRiderId() : dto.getRiderId();
        final Long deliveryRiderId = dto.getDeliveryRiderId() != null ? dto.getDeliveryRiderId() : dto.getRiderId();
        if (pickupRiderId == null && deliveryRiderId == null) {
            ApiResponse<OrderResponseDTO> r = new ApiResponse<>();
            r.setMessage("pickupRiderId or deliveryRiderId is required");
            r.setMessageKey("ERROR");
            r.setSuccess(false);
            r.setStatus(500);
            return r;
        }
        return orderService.adminAssignRiders(id, pickupRiderId, deliveryRiderId);
    }

    @PostMapping("/{id}/update-status")
    public ApiResponse<OrderResponseDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody AdminOrderStatusDTO dto) {
        if (dto.getStatus() == null || dto.getStatus().isBlank()) {
            ApiResponse<OrderResponseDTO> r = new ApiResponse<>();
            r.setMessage("status is required");
            r.setMessageKey("ERROR");
            r.setSuccess(false);
            r.setStatus(500);
            return r;
        }
        OrderStatus st = OrderStatus.valueOf(dto.getStatus().trim().toUpperCase());
        return orderService.adminUpdateStatus(id, st);
    }
}

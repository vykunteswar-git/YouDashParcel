package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AssignRiderRequestDTO;
import com.youdash.dto.HubStatusUpdateRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/admin/orders")
@Tag(name = "Admin — Orders", description = "Assignment and hub operations")
public class AdminOrderController {

    @Autowired
    private OrderService orderService;

    @GetMapping("/unassigned")
    @Operation(summary = "Orders ready for pickup rider assignment")
    public ApiResponse<List<OrderResponseDTO>> listUnassigned() {
        return orderService.listUnassignedOrders();
    }

    @PostMapping("/{orderId}/assign")
    @Operation(summary = "Assign pickup rider")
    public ApiResponse<OrderResponseDTO> assignPickupRider(
            @PathVariable Long orderId,
            @RequestBody AssignRiderRequestDTO body) {
        if (body == null || body.getRiderId() == null) {
            throw new RuntimeException("riderId is required");
        }
        return orderService.assignRider(orderId, body.getRiderId());
    }

    @PutMapping("/{orderId}/hub-status")
    @Operation(summary = "Advance hub line-haul status (outstation)")
    public ApiResponse<OrderResponseDTO> updateHubStatus(
            @PathVariable Long orderId,
            @RequestBody HubStatusUpdateRequestDTO body) {
        if (body == null || body.getStatus() == null) {
            throw new RuntimeException("status is required");
        }
        return orderService.updateHubStatus(orderId, body.getStatus());
    }

    @PutMapping("/{orderId}/complete-hub-delivery")
    @Operation(summary = "Hub-to-hub: mark delivered at destination hub")
    public ApiResponse<OrderResponseDTO> completeHubDelivery(@PathVariable Long orderId) {
        return orderService.completeHubDelivery(orderId);
    }

    @PutMapping("/{orderId}/ready-for-delivery")
    @Operation(summary = "Hub-to-door: mark ready for last-mile rider")
    public ApiResponse<OrderResponseDTO> readyForDelivery(@PathVariable Long orderId) {
        return orderService.markReadyForDelivery(orderId);
    }

    @PostMapping("/{orderId}/assign-delivery-rider")
    @Operation(summary = "Assign last-mile delivery rider")
    public ApiResponse<OrderResponseDTO> assignDeliveryRider(
            @PathVariable Long orderId,
            @RequestBody AssignRiderRequestDTO body) {
        if (body == null || body.getRiderId() == null) {
            throw new RuntimeException("riderId is required");
        }
        return orderService.assignDeliveryRider(orderId, body.getRiderId());
    }
}

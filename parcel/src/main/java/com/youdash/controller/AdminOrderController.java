package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminCreateH2hOrderRequestDTO;
import com.youdash.dto.AdminH2hPricePreviewRequestDTO;
import com.youdash.dto.AdminOrderAssignDTO;
import com.youdash.dto.AdminOrderStatusDTO;
import com.youdash.dto.AdminOrdersPageResponseDTO;
import com.youdash.dto.FinalPriceResponseDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.VerifyHubHandoverRequestDTO;
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
    public ApiResponse<AdminOrdersPageResponseDTO> listAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(name = "service_mode", required = false) String serviceMode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String payment,
            @RequestParam(required = false) String assigned,
            @RequestParam(required = false) String q) {
        return orderService.listAllOrdersAdmin(page, size, serviceMode, status, route, payment, assigned, q);
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponseDTO> getOne(@PathVariable Long id) {
        return orderService.getOrder(id, null, null, true);
    }

    @GetMapping("/by-ref")
    public ApiResponse<OrderResponseDTO> getByRef(@RequestParam String ref) {
        return orderService.getOrderByRef(ref);
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
        OrderStatus st = OrderStatus.fromLegacy(dto.getStatus());
        boolean override = Boolean.TRUE.equals(dto.getAdminOverride());
        return orderService.adminUpdateStatus(id, st, dto.getOtp(), override, dto.getCodCollectionMode());
    }

    @PostMapping("/{id}/verify-hub-handover")
    public ApiResponse<OrderResponseDTO> verifyHubHandover(
            @PathVariable Long id,
            @RequestBody VerifyHubHandoverRequestDTO dto) {
        return orderService.adminVerifyHubHandover(id, dto);
    }

    @PostMapping("/hub-to-hub/preview")
    public ApiResponse<FinalPriceResponseDTO> previewHubToHub(@RequestBody AdminH2hPricePreviewRequestDTO dto) {
        return orderService.adminPreviewHubToHubPrice(dto);
    }

    @PostMapping("/hub-to-hub")
    public ApiResponse<OrderResponseDTO> createHubToHub(@RequestBody AdminCreateH2hOrderRequestDTO dto) {
        return orderService.adminCreateHubToHubOrder(dto);
    }

    @DeleteMapping("/{id}/hub-to-hub")
    public ApiResponse<String> deleteHubToHub(@PathVariable Long id) {
        return orderService.adminDeleteHubToHubOrder(id);
    }

    @PatchMapping("/{id}/collect")
    public ApiResponse<OrderResponseDTO> collectH2hPayment(@PathVariable Long id) {
        return orderService.collectH2hPayment(id);
    }
}

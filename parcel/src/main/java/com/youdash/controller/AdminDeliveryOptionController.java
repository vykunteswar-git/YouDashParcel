package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.DeliveryOptionAdminResponseDTO;
import com.youdash.dto.DeliveryOptionRequestDTO;
import com.youdash.service.DeliveryOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/delivery-options")
@Tag(name = "Admin — Delivery options", description = "Configure incity/outstation keys returned by GET /api/delivery/options")
public class AdminDeliveryOptionController {

    @Autowired
    private DeliveryOptionService deliveryOptionService;

    @GetMapping
    @Operation(summary = "List all delivery option rows (active and inactive)")
    public ResponseEntity<ApiResponse<List<DeliveryOptionAdminResponseDTO>>> list() {
        List<DeliveryOptionAdminResponseDTO> list = deliveryOptionService.listAll();
        ApiResponse<List<DeliveryOptionAdminResponseDTO>> r = new ApiResponse<>();
        r.setData(list);
        r.setMessage("Delivery options fetched successfully");
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(HttpStatus.OK.value());
        r.setTotalCount(list.size());
        return ResponseEntity.ok(r);
    }

    @PostMapping
    @Operation(summary = "Create delivery option")
    public ResponseEntity<ApiResponse<DeliveryOptionAdminResponseDTO>> create(@Valid @RequestBody DeliveryOptionRequestDTO dto) {
        DeliveryOptionAdminResponseDTO created = deliveryOptionService.create(dto);
        return success(HttpStatus.CREATED, created, "Delivery option created successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update delivery option")
    public ResponseEntity<ApiResponse<DeliveryOptionAdminResponseDTO>> update(
            @PathVariable Long id, @Valid @RequestBody DeliveryOptionRequestDTO dto) {
        return success(HttpStatus.OK, deliveryOptionService.update(id, dto), "Delivery option updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate delivery option (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        deliveryOptionService.softDelete(id);
        ApiResponse<Void> r = new ApiResponse<>();
        r.setMessage("Delivery option deactivated successfully");
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(HttpStatus.OK.value());
        return ResponseEntity.ok(r);
    }

    private static <T> ResponseEntity<ApiResponse<T>> success(HttpStatus http, T data, String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.setData(data);
        r.setMessage(message);
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(http.value());
        return ResponseEntity.status(http).body(r);
    }
}

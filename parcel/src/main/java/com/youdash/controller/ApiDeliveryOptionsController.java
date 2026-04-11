package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.DeliveryOptionsResponseDTO;
import com.youdash.service.DeliveryOptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/delivery")
@Tag(name = "Delivery options", description = "Delivery mode labels for UI (configured via /admin/delivery-options)")
public class ApiDeliveryOptionsController {

    @Autowired
    private DeliveryOptionService deliveryOptionService;

    @GetMapping("/options")
    @Operation(summary = "List active incity and outstation delivery option keys")
    public ResponseEntity<ApiResponse<DeliveryOptionsResponseDTO>> options() {
        DeliveryOptionsResponseDTO data = deliveryOptionService.getPublicOptions();
        ApiResponse<DeliveryOptionsResponseDTO> r = new ApiResponse<>();
        r.setData(data);
        r.setMessage("Delivery options fetched successfully");
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(200);
        return ResponseEntity.ok(r);
    }
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.GlobalDeliveryConfigDTO;
import com.youdash.service.GlobalDeliveryConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/config/global")
@Tag(name = "Admin — Global delivery config", description = "Floors and incity extension defaults for pricing engine")
public class AdminGlobalDeliveryConfigController {

    @Autowired
    private GlobalDeliveryConfigService globalDeliveryConfigService;

    @GetMapping
    @Operation(summary = "Get active global delivery config")
    public ResponseEntity<ApiResponse<GlobalDeliveryConfigDTO>> get() {
        GlobalDeliveryConfigDTO data = globalDeliveryConfigService.getActive();
        return success(HttpStatus.OK, data, "Global config fetched successfully");
    }

    @PostMapping
    @Operation(summary = "Create new global config version (deactivates previous)")
    public ResponseEntity<ApiResponse<GlobalDeliveryConfigDTO>> post(@Valid @RequestBody GlobalDeliveryConfigDTO dto) {
        GlobalDeliveryConfigDTO created = globalDeliveryConfigService.create(dto);
        return success(HttpStatus.CREATED, created, "Global config created successfully");
    }

    @PutMapping
    @Operation(summary = "Update active global config")
    public ResponseEntity<ApiResponse<GlobalDeliveryConfigDTO>> put(@Valid @RequestBody GlobalDeliveryConfigDTO dto) {
        GlobalDeliveryConfigDTO updated = globalDeliveryConfigService.update(dto);
        return success(HttpStatus.OK, updated, "Global config updated successfully");
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

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.WeightPricingConfigDTO;
import com.youdash.service.WeightPricingConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/pricing/weight")
@Tag(name = "Admin — Weight pricing", description = "Global weight surcharge (PER_KG)")
public class AdminWeightPricingController {

    @Autowired
    private WeightPricingConfigService weightPricingConfigService;

    @GetMapping
    @Operation(summary = "Get active weight pricing config")
    public ResponseEntity<ApiResponse<WeightPricingConfigDTO>> get() {
        WeightPricingConfigDTO data = weightPricingConfigService.getActive();
        return success(HttpStatus.OK, data, "Weight pricing config fetched successfully");
    }

    @PostMapping
    @Operation(summary = "Create new weight pricing version (deactivates previous)")
    public ResponseEntity<ApiResponse<WeightPricingConfigDTO>> post(@Valid @RequestBody WeightPricingConfigDTO dto) {
        WeightPricingConfigDTO created = weightPricingConfigService.create(dto);
        return success(HttpStatus.CREATED, created, "Weight pricing config created successfully");
    }

    @PutMapping
    @Operation(summary = "Update active weight pricing config")
    public ResponseEntity<ApiResponse<WeightPricingConfigDTO>> put(@Valid @RequestBody WeightPricingConfigDTO dto) {
        WeightPricingConfigDTO updated = weightPricingConfigService.update(dto);
        return success(HttpStatus.OK, updated, "Weight pricing config updated successfully");
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

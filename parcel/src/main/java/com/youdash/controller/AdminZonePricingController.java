package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZonePricingRequestDTO;
import com.youdash.dto.ZonePricingResponseDTO;
import com.youdash.service.ZonePricingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/pricing/zone")
@Tag(name = "Admin — Zone pricing", description = "Pickup/delivery per-km rates per zone (Module 1)")
public class AdminZonePricingController {

    @Autowired
    private ZonePricingService zonePricingService;

    @PostMapping
    @Operation(summary = "Create zone pricing (zone_id must exist; one active row per zone)")
    public ResponseEntity<ApiResponse<ZonePricingResponseDTO>> create(@Valid @RequestBody ZonePricingRequestDTO dto) {
        ZonePricingResponseDTO created = zonePricingService.create(dto);
        return success(HttpStatus.CREATED, created, "Zone pricing created successfully");
    }

    @GetMapping
    @Operation(summary = "List all zone pricing rows")
    public ResponseEntity<ApiResponse<List<ZonePricingResponseDTO>>> list() {
        List<ZonePricingResponseDTO> list = zonePricingService.listAll();
        ApiResponse<List<ZonePricingResponseDTO>> r = new ApiResponse<>();
        r.setData(list);
        r.setMessage("Zone pricing list fetched successfully");
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(HttpStatus.OK.value());
        r.setTotalCount(list.size());
        return ResponseEntity.ok(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update zone pricing")
    public ResponseEntity<ApiResponse<ZonePricingResponseDTO>> update(
            @PathVariable Long id, @Valid @RequestBody ZonePricingRequestDTO dto) {
        return success(HttpStatus.OK, zonePricingService.update(id, dto), "Zone pricing updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate zone pricing (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        zonePricingService.softDelete(id);
        ApiResponse<Void> r = new ApiResponse<>();
        r.setMessage("Zone pricing deactivated successfully");
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

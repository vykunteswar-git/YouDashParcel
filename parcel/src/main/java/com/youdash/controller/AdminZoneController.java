package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRequestDTO;
import com.youdash.dto.ZoneResponseDTO;
import com.youdash.service.ZoneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/zones")
@Tag(name = "Admin — Zones", description = "Incity delivery: serviceable areas per city")
public class AdminZoneController {

    @Autowired
    private ZoneService zoneService;

    @PostMapping
    @Operation(summary = "Create zone")
    public ResponseEntity<ApiResponse<ZoneResponseDTO>> create(@Valid @RequestBody ZoneRequestDTO dto) {
        ZoneResponseDTO created = zoneService.create(dto);
        return success(HttpStatus.CREATED, created, "Zone created successfully");
    }

    @GetMapping
    @Operation(summary = "List zones", description = "Optional filter by city (case-insensitive)")
    public ResponseEntity<ApiResponse<List<ZoneResponseDTO>>> list(
            @Parameter(description = "Filter by city name") @RequestParam(required = false) String city) {
        List<ZoneResponseDTO> list = zoneService.listAll(city);
        return success(HttpStatus.OK, list, "Zones fetched successfully");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get zone by id")
    public ResponseEntity<ApiResponse<ZoneResponseDTO>> getById(@PathVariable Long id) {
        return success(HttpStatus.OK, zoneService.getById(id), "Zone fetched successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update zone")
    public ResponseEntity<ApiResponse<ZoneResponseDTO>> update(
            @PathVariable Long id, @Valid @RequestBody ZoneRequestDTO dto) {
        return success(HttpStatus.OK, zoneService.update(id, dto), "Zone updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete zone (removes zone and its zone-pricing rows)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        zoneService.delete(id);
        ApiResponse<Void> r = new ApiResponse<>();
        r.setMessage("Zone deleted successfully");
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

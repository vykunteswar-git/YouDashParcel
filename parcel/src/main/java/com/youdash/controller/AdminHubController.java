package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRequestDTO;
import com.youdash.dto.HubResponseDTO;
import com.youdash.dto.HubStatusPatchDTO;
import com.youdash.service.HubService;
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
@RequestMapping("/admin/hubs")
@Tag(name = "Admin — Hubs", description = "Outstation delivery: routing hubs per city")
public class AdminHubController {

    @Autowired
    private HubService hubService;

    @PostMapping
    @Operation(summary = "Create hub")
    public ResponseEntity<ApiResponse<HubResponseDTO>> create(@Valid @RequestBody HubRequestDTO dto) {
        HubResponseDTO created = hubService.create(dto);
        return success(HttpStatus.CREATED, created, "Hub created successfully");
    }

    @GetMapping
    @Operation(summary = "List hubs", description = "Optional filter by city (case-insensitive)")
    public ResponseEntity<ApiResponse<List<HubResponseDTO>>> list(
            @Parameter(description = "Filter by city name") @RequestParam(required = false) String city) {
        List<HubResponseDTO> list = hubService.listAll(city);
        return success(HttpStatus.OK, list, "Hubs fetched successfully");
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get hub by id")
    public ResponseEntity<ApiResponse<HubResponseDTO>> getById(@PathVariable Long id) {
        return success(HttpStatus.OK, hubService.getById(id), "Hub fetched successfully");
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update hub")
    public ResponseEntity<ApiResponse<HubResponseDTO>> update(
            @PathVariable Long id, @Valid @RequestBody HubRequestDTO dto) {
        return success(HttpStatus.OK, hubService.update(id, dto), "Hub updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate hub (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        hubService.softDelete(id);
        ApiResponse<Void> r = new ApiResponse<>();
        r.setMessage("Hub deactivated successfully");
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(HttpStatus.OK.value());
        return ResponseEntity.ok(r);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Set hub active flag")
    public ResponseEntity<ApiResponse<HubResponseDTO>> patchStatus(
            @PathVariable Long id, @Valid @RequestBody HubStatusPatchDTO dto) {
        return success(HttpStatus.OK, hubService.updateStatus(id, dto), "Hub status updated successfully");
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

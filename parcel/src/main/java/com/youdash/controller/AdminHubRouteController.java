package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteRequestDTO;
import com.youdash.dto.HubRouteResponseDTO;
import com.youdash.service.HubRouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/routes")
@Tag(name = "Admin — Hub routes", description = "Outstation legs between hubs (Module 2)")
public class AdminHubRouteController {

    @Autowired
    private HubRouteService hubRouteService;

    @PostMapping
    @Operation(summary = "Create hub route (hub ids must exist)")
    public ResponseEntity<ApiResponse<HubRouteResponseDTO>> create(@Valid @RequestBody HubRouteRequestDTO dto) {
        HubRouteResponseDTO created = hubRouteService.create(dto);
        return success(HttpStatus.CREATED, created, "Route created successfully");
    }

    @GetMapping
    @Operation(summary = "List all hub routes")
    public ResponseEntity<ApiResponse<List<HubRouteResponseDTO>>> list() {
        List<HubRouteResponseDTO> list = hubRouteService.listAll();
        ApiResponse<List<HubRouteResponseDTO>> r = new ApiResponse<>();
        r.setData(list);
        r.setMessage("Routes fetched successfully");
        r.setMessageKey("SUCCESS");
        r.setSuccess(true);
        r.setStatus(HttpStatus.OK.value());
        r.setTotalCount(list.size());
        return ResponseEntity.ok(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update hub route")
    public ResponseEntity<ApiResponse<HubRouteResponseDTO>> update(
            @PathVariable Long id, @Valid @RequestBody HubRouteRequestDTO dto) {
        return success(HttpStatus.OK, hubRouteService.update(id, dto), "Route updated successfully");
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Deactivate route (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        hubRouteService.softDelete(id);
        ApiResponse<Void> r = new ApiResponse<>();
        r.setMessage("Route deactivated successfully");
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

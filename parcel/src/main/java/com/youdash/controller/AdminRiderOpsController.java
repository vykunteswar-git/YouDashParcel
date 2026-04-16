package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.service.RiderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/admin/riders")
@Tag(name = "Admin — Riders", description = "Approval and assignment pool")
public class AdminRiderOpsController {

    @Autowired
    private RiderService riderService;

    @GetMapping("/pending")
    @Operation(summary = "Riders awaiting approval")
    public ApiResponse<List<RiderResponseDTO>> pending() {
        return riderService.listPendingRiders();
    }

    @GetMapping
    @Operation(summary = "List riders filtered by approval status")
    public ApiResponse<List<RiderResponseDTO>> listByStatus(@RequestParam(name = "status", required = false) String status) {
        if (status == null || status.isBlank()) {
            // Backward compatible: default to pending
            return riderService.listPendingRiders();
        }
        return riderService.listByApprovalStatus(status.trim());
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve rider")
    public ApiResponse<RiderResponseDTO> approve(@PathVariable Long id) {
        return riderService.approveRider(id);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject rider")
    public ApiResponse<RiderResponseDTO> reject(@PathVariable Long id) {
        return riderService.rejectRider(id);
    }

    @PostMapping("/available")
    @Operation(summary = "Approved, available, not busy on active orders")
    public ApiResponse<List<RiderResponseDTO>> available() {
        return riderService.listRidersEligibleForAssignment();
    }
}

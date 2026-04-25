package com.youdash.controller.wallet;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.incentive.PeakIncentiveCampaignDTO;
import com.youdash.service.PeakIncentiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/incentives")
@Tag(name = "Admin — Rider Incentives", description = "Create and manage peak-hour bonus campaigns.")
public class AdminIncentiveController {

    @Autowired
    private PeakIncentiveService peakIncentiveService;

    @GetMapping("/peak-campaigns")
    @Operation(summary = "List peak incentive campaigns")
    public ApiResponse<List<PeakIncentiveCampaignDTO>> list() {
        return peakIncentiveService.adminList();
    }

    @PostMapping("/peak-campaigns")
    @Operation(summary = "Create peak incentive campaign")
    public ApiResponse<PeakIncentiveCampaignDTO> create(@RequestBody PeakIncentiveCampaignDTO dto) {
        return peakIncentiveService.adminCreate(dto);
    }

    @PutMapping("/peak-campaigns/{id}")
    @Operation(summary = "Update peak incentive campaign")
    public ApiResponse<PeakIncentiveCampaignDTO> update(@PathVariable Long id, @RequestBody PeakIncentiveCampaignDTO dto) {
        return peakIncentiveService.adminUpdate(id, dto);
    }

    @DeleteMapping("/peak-campaigns/{id}")
    @Operation(summary = "Delete peak incentive campaign")
    public ApiResponse<String> delete(@PathVariable Long id) {
        return peakIncentiveService.adminDelete(id);
    }
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminDashboardActivityDTO;
import com.youdash.dto.admin.AdminDashboardSummaryDTO;
import com.youdash.dto.admin.AdminDashboardTrendPointDTO;
import com.youdash.service.AdminDashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private AdminDashboardService adminDashboardService;

    @GetMapping("/summary")
    public ApiResponse<AdminDashboardSummaryDTO> summary(
            @RequestParam(name = "range", required = false, defaultValue = "TODAY") String range) {
        return adminDashboardService.getSummary(range);
    }

    @GetMapping("/order-volume")
    public ApiResponse<List<AdminDashboardTrendPointDTO>> orderVolume(
            @RequestParam(name = "range", required = false, defaultValue = "TODAY") String range) {
        return adminDashboardService.getOrderVolumeTrend(range);
    }

    @GetMapping("/live-activity")
    public ApiResponse<List<AdminDashboardActivityDTO>> liveActivity(
            @RequestParam(name = "limit", required = false, defaultValue = "10") int limit) {
        return adminDashboardService.getLiveActivity(limit);
    }
}


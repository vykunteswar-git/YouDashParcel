package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminRevenueReportDTO;
import com.youdash.service.AdminReportsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/reports")
public class AdminReportsController {

    @Autowired
    private AdminReportsService adminReportsService;

    @GetMapping("/revenue")
    public ApiResponse<AdminRevenueReportDTO> revenue(
            @RequestParam(name = "range", required = false, defaultValue = "THIS_WEEK") String range) {
        return adminReportsService.getRevenueReport(range);
    }
}


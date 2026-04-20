package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminRevenueReportDTO;

public interface AdminReportsService {
    ApiResponse<AdminRevenueReportDTO> getRevenueReport(String range);
}


package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminDashboardActivityDTO;
import com.youdash.dto.admin.AdminDashboardSummaryDTO;
import com.youdash.dto.admin.AdminDashboardTrendPointDTO;

import java.util.List;

public interface AdminDashboardService {
    ApiResponse<AdminDashboardSummaryDTO> getSummary(String range);

    ApiResponse<List<AdminDashboardTrendPointDTO>> getOrderVolumeTrend(String range);

    ApiResponse<List<AdminDashboardActivityDTO>> getLiveActivity(int limit);
}


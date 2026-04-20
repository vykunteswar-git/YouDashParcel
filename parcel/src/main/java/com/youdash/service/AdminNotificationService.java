package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.notification.AdminNotificationBroadcastRequestDTO;
import com.youdash.dto.notification.AdminNotificationCampaignDTO;
import com.youdash.dto.notification.AdminNotificationTargetsDTO;

import java.util.List;

public interface AdminNotificationService {
    ApiResponse<AdminNotificationCampaignDTO> sendBroadcast(Long adminUserId, AdminNotificationBroadcastRequestDTO dto);

    ApiResponse<List<AdminNotificationCampaignDTO>> listCampaigns(int page, int size);

    ApiResponse<AdminNotificationTargetsDTO> getTargets(String q, int limit);
}


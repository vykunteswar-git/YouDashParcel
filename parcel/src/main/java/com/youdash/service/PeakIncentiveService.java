package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.incentive.PeakIncentiveCampaignDTO;
import com.youdash.entity.OrderEntity;

import java.time.Instant;
import java.util.List;

public interface PeakIncentiveService {
    ApiResponse<List<PeakIncentiveCampaignDTO>> adminList();

    ApiResponse<PeakIncentiveCampaignDTO> adminCreate(PeakIncentiveCampaignDTO dto);

    ApiResponse<PeakIncentiveCampaignDTO> adminUpdate(Long id, PeakIncentiveCampaignDTO dto);

    ApiResponse<String> adminDelete(Long id);

    ApiResponse<List<PeakIncentiveCampaignDTO>> riderProgress(Long riderId);

    double resolveBonusForDeliveredOrder(OrderEntity order, Instant deliveredAt);
}

package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.rating.RiderRatingRequestDTO;
import com.youdash.dto.rating.RiderRatingSummaryDTO;

public interface RiderRatingService {
    ApiResponse<String> submitUserRating(Long orderId, Long userId, RiderRatingRequestDTO dto);

    ApiResponse<RiderRatingSummaryDTO> getRiderRatingSummary(Long riderId);
}

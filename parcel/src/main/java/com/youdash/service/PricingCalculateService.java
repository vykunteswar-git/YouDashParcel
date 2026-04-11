package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PricingCalculateRequestDTO;
import com.youdash.dto.PricingCalculateResponseDTO;

public interface PricingCalculateService {

    ApiResponse<PricingCalculateResponseDTO> calculate(PricingCalculateRequestDTO dto);

    /**
     * Same computation as HTTP API; throws {@link RuntimeException} on validation / config errors.
     */
    PricingCalculateResponseDTO computePricing(PricingCalculateRequestDTO dto);
}

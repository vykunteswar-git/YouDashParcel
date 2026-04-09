package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FareCalculateRequestDTO;
import com.youdash.dto.FareCalculateResponseDTO;

public interface FareService {
    ApiResponse<FareCalculateResponseDTO> calculateFare(FareCalculateRequestDTO dto);
}


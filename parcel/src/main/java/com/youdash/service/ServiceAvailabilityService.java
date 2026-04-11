package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ServiceAvailabilityRequestDTO;
import com.youdash.dto.ServiceAvailabilityResponseDTO;

public interface ServiceAvailabilityService {

    ApiResponse<ServiceAvailabilityResponseDTO> check(ServiceAvailabilityRequestDTO dto);
}

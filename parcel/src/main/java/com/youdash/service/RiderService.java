package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;

public interface RiderService {

    ApiResponse<RiderResponseDTO> createRider(RiderRequestDTO dto);

    ApiResponse<List<RiderResponseDTO>> getAllRiders();

    ApiResponse<List<RiderResponseDTO>> getAvailableRiders();

    ApiResponse<RiderResponseDTO> updateAvailability(Long riderId, Boolean status);

    ApiResponse<RiderResponseDTO> updateLocation(Long riderId, Double lat, Double lng);

}

package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderOnlineTimeDTO;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.dto.RiderSelfUpdateDTO;
import com.youdash.entity.RiderEntity;

public interface RiderService {

    ApiResponse<RiderResponseDTO> createRider(RiderRequestDTO dto);

    ApiResponse<RiderResponseDTO> getRiderProfile(RiderEntity rider);

    ApiResponse<List<RiderResponseDTO>> getAllRiders();

    ApiResponse<List<RiderResponseDTO>> getAvailableRiders();

    ApiResponse<RiderResponseDTO> updateAvailability(Long riderId, Boolean status);

    ApiResponse<RiderResponseDTO> updateLocation(Long riderId, Double lat, Double lng);

    ApiResponse<RiderResponseDTO> patchSelfProfile(Long riderId, RiderSelfUpdateDTO dto);

    ApiResponse<List<RiderResponseDTO>> listPendingRiders();

    ApiResponse<List<RiderResponseDTO>> listByApprovalStatus(String approvalStatus);

    ApiResponse<RiderResponseDTO> approveRider(Long id);

    ApiResponse<RiderResponseDTO> rejectRider(Long id);

    ApiResponse<List<RiderResponseDTO>> listRidersEligibleForAssignment();

    ApiResponse<RiderOnlineTimeDTO> getOnlineTimeForDate(Long riderId, String dateIso);

}

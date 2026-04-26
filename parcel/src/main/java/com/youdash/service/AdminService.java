package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.VehicleDTO;

import com.youdash.dto.AdminLoginDTO;
import com.youdash.dto.AdminResponseDTO;

import java.util.List;

public interface AdminService {
    // Admin Authentication
    ApiResponse<AdminResponseDTO> login(AdminLoginDTO dto);

    // Vehicle Management
    ApiResponse<VehicleDTO> createVehicle(VehicleDTO dto);
    ApiResponse<List<VehicleDTO>> getAllVehicles();
    ApiResponse<List<VehicleDTO>> getActiveVehicles();
    ApiResponse<VehicleDTO> updateVehicle(Long id, VehicleDTO dto);

    // User Management
    /** Permanently remove a user row. Fails cleanly if FK constraints exist. */
    ApiResponse<String> hardDeleteUser(Long userId);
}

package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PackageItemDTO;
import com.youdash.dto.VehicleDTO;

import java.util.List;

public interface AdminService {
    // Vehicle Management
    ApiResponse<VehicleDTO> createVehicle(VehicleDTO dto);
    ApiResponse<List<VehicleDTO>> getAllVehicles();
    ApiResponse<List<VehicleDTO>> getActiveVehicles();
    ApiResponse<VehicleDTO> updateVehicle(Long id, VehicleDTO dto);
    ApiResponse<VehicleDTO> toggleVehicle(Long id);

    // Category Management
    ApiResponse<PackageCategoryDTO> createCategory(PackageCategoryDTO dto);
    ApiResponse<List<PackageCategoryDTO>> getAllCategories();
    ApiResponse<List<PackageCategoryDTO>> getActiveCategories();
    ApiResponse<PackageCategoryDTO> updateCategory(Long id, PackageCategoryDTO dto);
    ApiResponse<PackageCategoryDTO> toggleCategory(Long id);

    // Package Item Management
    ApiResponse<PackageItemDTO> createPackageItem(PackageItemDTO dto);
    ApiResponse<List<PackageItemDTO>> getAllPackageItems();
    ApiResponse<PackageItemDTO> updatePackageItem(Long id, PackageItemDTO dto);
    ApiResponse<PackageItemDTO> togglePackageItem(Long id);
}

package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PackageCategoryRequestDTO;

import java.util.List;

public interface PackageCategoryService {

    ApiResponse<List<PackageCategoryDTO>> listActivePublic();

    ApiResponse<List<PackageCategoryDTO>> listAllAdmin();

    ApiResponse<PackageCategoryDTO> createAdmin(PackageCategoryRequestDTO dto);

    ApiResponse<PackageCategoryDTO> updateAdmin(Long id, PackageCategoryRequestDTO dto);

    ApiResponse<Void> deleteAdmin(Long id);
}

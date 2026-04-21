package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.BannerRequestDTO;

import java.util.List;

public interface BannerService {
    ApiResponse<List<BannerDTO>> listPublicActive();

    ApiResponse<List<BannerDTO>> listAdmin();

    ApiResponse<BannerDTO> createAdmin(BannerRequestDTO dto);

    ApiResponse<BannerDTO> updateAdmin(Long id, BannerRequestDTO dto);

    ApiResponse<Void> deleteAdmin(Long id);
}

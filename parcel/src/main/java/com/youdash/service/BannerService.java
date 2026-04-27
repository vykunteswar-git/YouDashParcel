package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.BannerRequestDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface BannerService {
    ApiResponse<List<BannerDTO>> listPublicActive();

    ApiResponse<List<BannerDTO>> listAdmin();

    ApiResponse<BannerDTO> createAdmin(BannerRequestDTO dto, MultipartFile imageFile);

    ApiResponse<BannerDTO> updateAdmin(Long id, BannerRequestDTO dto, MultipartFile imageFile);

    ApiResponse<Void> deleteAdmin(Long id);
}

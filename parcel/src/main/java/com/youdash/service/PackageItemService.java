package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageItemResponseDTO;
import java.util.List;

public interface PackageItemService {
    ApiResponse<List<PackageItemResponseDTO>> getItemsByCategoryId(Long categoryId);
}

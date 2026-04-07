package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageItemResponseDTO;
import com.youdash.entity.PackageItemEntity;
import com.youdash.repository.PackageItemRepository;
import com.youdash.service.PackageItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class PackageItemServiceImpl implements PackageItemService {

    @Autowired
    private PackageItemRepository packageItemRepository;

    @Override
    public ApiResponse<List<PackageItemResponseDTO>> getItemsByCategoryId(Long categoryId) {
        ApiResponse<List<PackageItemResponseDTO>> response = new ApiResponse<>();
        try {
            List<PackageItemEntity> items = packageItemRepository.findByPackageCategoryIdAndIsActiveTrue(categoryId);
            List<PackageItemResponseDTO> dtos = items.stream().map(item -> {
                PackageItemResponseDTO dto = new PackageItemResponseDTO();
                dto.setId(item.getId());
                dto.setName(item.getName());
                dto.setImageUrl(item.getImageUrl());
                return dto;
            }).collect(Collectors.toList());

            response.setData(dtos);
            response.setMessage("Package items fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage("Failed to fetch package items: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }
}

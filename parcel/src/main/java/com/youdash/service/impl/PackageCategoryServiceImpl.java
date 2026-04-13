package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PackageCategoryRequestDTO;
import com.youdash.entity.PackageCategoryEntity;
import com.youdash.repository.PackageCategoryRepository;
import com.youdash.service.PackageCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PackageCategoryServiceImpl implements PackageCategoryService {

    @Autowired
    private PackageCategoryRepository packageCategoryRepository;

    @Override
    public ApiResponse<List<PackageCategoryDTO>> listActivePublic() {
        ApiResponse<List<PackageCategoryDTO>> response = new ApiResponse<>();
        try {
            List<PackageCategoryDTO> list = packageCategoryRepository
                    .findByIsActiveTrueOrderBySortOrderAscIdAsc().stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    @Override
    public ApiResponse<List<PackageCategoryDTO>> listAllAdmin() {
        ApiResponse<List<PackageCategoryDTO>> response = new ApiResponse<>();
        try {
            List<PackageCategoryDTO> list = packageCategoryRepository
                    .findAll(Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("id"))).stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<PackageCategoryDTO> createAdmin(PackageCategoryRequestDTO dto) {
        ApiResponse<PackageCategoryDTO> response = new ApiResponse<>();
        try {
            validateRequest(dto, true);
            PackageCategoryEntity e = new PackageCategoryEntity();
            applyRequest(e, dto, true);
            if (e.getIsActive() == null) {
                e.setIsActive(Boolean.TRUE);
            }
            if (e.getSortOrder() == null) {
                e.setSortOrder((int) packageCategoryRepository.count() + 1);
            }
            PackageCategoryEntity saved = packageCategoryRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Category created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<PackageCategoryDTO> updateAdmin(Long id, PackageCategoryRequestDTO dto) {
        ApiResponse<PackageCategoryDTO> response = new ApiResponse<>();
        try {
            PackageCategoryEntity e = packageCategoryRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Category not found"));
            validateRequest(dto, false);
            applyRequest(e, dto, false);
            PackageCategoryEntity saved = packageCategoryRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Category updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<Void> deleteAdmin(Long id) {
        ApiResponse<Void> response = new ApiResponse<>();
        try {
            if (!packageCategoryRepository.existsById(Objects.requireNonNull(id))) {
                throw new RuntimeException("Category not found");
            }
            packageCategoryRepository.deleteById(id);
            response.setMessage("Category deleted");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    private void validateRequest(PackageCategoryRequestDTO dto, boolean create) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new RuntimeException("name is required");
        }
        if (create && (dto.getEmoji() == null || dto.getEmoji().isBlank())) {
            throw new RuntimeException("emoji is required");
        }
    }

    private void applyRequest(PackageCategoryEntity e, PackageCategoryRequestDTO dto, boolean create) {
        e.setName(dto.getName().trim());
        if (create) {
            String em = dto.getEmoji().trim();
            e.setEmoji(em.isEmpty() ? null : em);
        } else if (dto.getEmoji() != null) {
            String em = dto.getEmoji().trim();
            e.setEmoji(em.isEmpty() ? null : em);
        }
        if (dto.getSortOrder() != null) {
            e.setSortOrder(dto.getSortOrder());
        }
        if (dto.getIsActive() != null) {
            e.setIsActive(dto.getIsActive());
        }
        if (dto.getDefaultDeliveryType() != null) {
            String dt = dto.getDefaultDeliveryType().trim();
            e.setDefaultDeliveryType(dt.isEmpty() ? null : dt);
        }
    }

    private PackageCategoryDTO toDto(PackageCategoryEntity e) {
        return PackageCategoryDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .emoji(e.getEmoji())
                .sortOrder(e.getSortOrder())
                .isActive(e.getIsActive())
                .defaultDeliveryType(e.getDefaultDeliveryType())
                .build();
    }

    private static void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

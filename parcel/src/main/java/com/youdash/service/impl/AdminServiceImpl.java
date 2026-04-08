package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PackageItemDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.entity.PackageCategoryEntity;
import com.youdash.entity.PackageItemEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.repository.PackageCategoryRepository;
import com.youdash.repository.PackageItemRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PackageCategoryRepository packageCategoryRepository;

    @Autowired
    private PackageItemRepository packageItemRepository;

    // --- VEHICLE MANAGEMENT ---

    @Override
    public ApiResponse<VehicleDTO> createVehicle(VehicleDTO dto) {
        ApiResponse<VehicleDTO> response = new ApiResponse<>();
        try {
            validateVehicle(dto);
            VehicleEntity entity = new VehicleEntity();
            entity.setName(dto.getName());
            entity.setPricePerKm(dto.getPricePerKm());
            entity.setMaxWeight(dto.getMaxWeight());
            entity.setImageUrl(dto.getImageUrl());
            entity.setIsActive(true); // Default

            VehicleEntity saved = vehicleRepository.save(entity);
            response.setData(mapToVehicleDTO(saved));
            response.setMessage("Vehicle created successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<VehicleDTO>> getAllVehicles() {
        ApiResponse<List<VehicleDTO>> response = new ApiResponse<>();
        try {
            List<VehicleEntity> vehicles = vehicleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            List<VehicleDTO> dtos = vehicles.stream().map(this::mapToVehicleDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Vehicles fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<VehicleDTO>> getActiveVehicles() {
        ApiResponse<List<VehicleDTO>> response = new ApiResponse<>();
        try {
            List<VehicleEntity> vehicles = vehicleRepository.findByIsActiveTrue();
            List<VehicleDTO> dtos = vehicles.stream().map(this::mapToVehicleDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Active vehicles fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<VehicleDTO> updateVehicle(Long id, VehicleDTO dto) {
        ApiResponse<VehicleDTO> response = new ApiResponse<>();
        try {
            VehicleEntity entity = vehicleRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));
            
            if (dto.getName() != null && !dto.getName().isEmpty()) entity.setName(dto.getName());
            if (dto.getPricePerKm() != null) {
                if (dto.getPricePerKm() <= 0) throw new RuntimeException("Price per Km must be > 0");
                entity.setPricePerKm(dto.getPricePerKm());
            }
            if (dto.getMaxWeight() != null) entity.setMaxWeight(dto.getMaxWeight());
            if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) entity.setImageUrl(dto.getImageUrl());

            VehicleEntity updated = vehicleRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToVehicleDTO(updated));
            response.setMessage("Vehicle updated successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<VehicleDTO> toggleVehicle(Long id) {
        ApiResponse<VehicleDTO> response = new ApiResponse<>();
        try {
            VehicleEntity entity = vehicleRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));
            entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
            VehicleEntity updated = vehicleRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToVehicleDTO(updated));
            response.setMessage("Vehicle status toggled successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    // --- CATEGORY MANAGEMENT ---

    @Override
    public ApiResponse<PackageCategoryDTO> createCategory(PackageCategoryDTO dto) {
        ApiResponse<PackageCategoryDTO> response = new ApiResponse<>();
        try {
            validateCategory(dto);
            PackageCategoryEntity entity = new PackageCategoryEntity();
            entity.setName(dto.getName());
            entity.setImageUrl(dto.getImageUrl());
            entity.setDefaultDescription(dto.getDefaultDescription());
            entity.setIsActive(true); // Default

            PackageCategoryEntity saved = packageCategoryRepository.save(entity);
            response.setData(mapToCategoryDTO(saved));
            response.setMessage("Category created successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<PackageCategoryDTO>> getAllCategories() {
        ApiResponse<List<PackageCategoryDTO>> response = new ApiResponse<>();
        try {
            List<PackageCategoryEntity> categories = packageCategoryRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            List<PackageCategoryDTO> dtos = categories.stream().map(this::mapToCategoryDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Categories fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<PackageCategoryDTO>> getActiveCategories() {
        ApiResponse<List<PackageCategoryDTO>> response = new ApiResponse<>();
        try {
            List<PackageCategoryEntity> categories = packageCategoryRepository.findByIsActiveTrue();
            List<PackageCategoryDTO> dtos = categories.stream().map(this::mapToCategoryDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Active categories fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<PackageCategoryDTO> updateCategory(Long id, PackageCategoryDTO dto) {
        ApiResponse<PackageCategoryDTO> response = new ApiResponse<>();
        try {
            PackageCategoryEntity entity = packageCategoryRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
            
            if (dto.getName() != null && !dto.getName().isEmpty()) entity.setName(dto.getName());
            if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) entity.setImageUrl(dto.getImageUrl());
            if (dto.getDefaultDescription() != null) entity.setDefaultDescription(dto.getDefaultDescription());

            PackageCategoryEntity updated = packageCategoryRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToCategoryDTO(updated));
            response.setMessage("Category updated successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<PackageCategoryDTO> toggleCategory(Long id) {
        ApiResponse<PackageCategoryDTO> response = new ApiResponse<>();
        try {
            PackageCategoryEntity entity = packageCategoryRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + id));
            entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
            PackageCategoryEntity updated = packageCategoryRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToCategoryDTO(updated));
            response.setMessage("Category status toggled successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    // --- PACKAGE ITEM MANAGEMENT ---

    @Override
    public ApiResponse<PackageItemDTO> createPackageItem(PackageItemDTO dto) {
        ApiResponse<PackageItemDTO> response = new ApiResponse<>();
        try {
            validatePackageItem(dto);
            // Check if category exists
            packageCategoryRepository.findById(Objects.requireNonNull(dto.getPackageCategoryId()))
                    .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getPackageCategoryId()));

            PackageItemEntity entity = new PackageItemEntity();
            entity.setName(dto.getName());
            entity.setImageUrl(dto.getImageUrl());
            entity.setPackageCategoryId(dto.getPackageCategoryId());
            entity.setIsActive(true); // Default

            PackageItemEntity saved = packageItemRepository.save(entity);
            response.setData(mapToItemDTO(saved));
            response.setMessage("Package item created successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<PackageItemDTO>> getAllPackageItems() {
        ApiResponse<List<PackageItemDTO>> response = new ApiResponse<>();
        try {
            List<PackageItemEntity> items = packageItemRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            List<PackageItemDTO> dtos = items.stream().map(this::mapToItemDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Package items fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<PackageItemDTO> updatePackageItem(Long id, PackageItemDTO dto) {
        ApiResponse<PackageItemDTO> response = new ApiResponse<>();
        try {
            PackageItemEntity entity = packageItemRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Package item not found with id: " + id));
            
            if (dto.getName() != null && !dto.getName().isEmpty()) entity.setName(dto.getName());
            if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) entity.setImageUrl(dto.getImageUrl());
            if (dto.getPackageCategoryId() != null) {
                // Check if new category exists
                packageCategoryRepository.findById(Objects.requireNonNull(dto.getPackageCategoryId()))
                        .orElseThrow(() -> new RuntimeException("Category not found with id: " + dto.getPackageCategoryId()));
                entity.setPackageCategoryId(dto.getPackageCategoryId());
            }

            PackageItemEntity updated = packageItemRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToItemDTO(updated));
            response.setMessage("Package item updated successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<PackageItemDTO> togglePackageItem(Long id) {
        ApiResponse<PackageItemDTO> response = new ApiResponse<>();
        try {
            PackageItemEntity entity = packageItemRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Package item not found with id: " + id));
            entity.setIsActive(!Boolean.TRUE.equals(entity.getIsActive()));
            PackageItemEntity updated = packageItemRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToItemDTO(updated));
            response.setMessage("Package item status toggled successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    // --- HELPER METHODS (Manual Mapping & Validation) ---

    private void validateVehicle(VehicleDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) throw new RuntimeException("Name is required");
        if (dto.getImageUrl() == null || dto.getImageUrl().trim().isEmpty()) throw new RuntimeException("Image URL is required");
        if (dto.getPricePerKm() == null || dto.getPricePerKm() <= 0) throw new RuntimeException("Price per Km must be > 0");
    }

    private void validateCategory(PackageCategoryDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) throw new RuntimeException("Name is required");
        if (dto.getImageUrl() == null || dto.getImageUrl().trim().isEmpty()) throw new RuntimeException("Image URL is required");
    }

    private void validatePackageItem(PackageItemDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty()) throw new RuntimeException("Name is required");
        if (dto.getImageUrl() == null || dto.getImageUrl().trim().isEmpty()) throw new RuntimeException("Image URL is required");
        if (dto.getPackageCategoryId() == null) throw new RuntimeException("Package Category ID is required");
    }

    private VehicleDTO mapToVehicleDTO(VehicleEntity entity) {
        VehicleDTO dto = new VehicleDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPricePerKm(entity.getPricePerKm());
        dto.setMaxWeight(entity.getMaxWeight());
        dto.setImageUrl(entity.getImageUrl());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    private PackageCategoryDTO mapToCategoryDTO(PackageCategoryEntity entity) {
        PackageCategoryDTO dto = new PackageCategoryDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setImageUrl(entity.getImageUrl());
        dto.setDefaultDescription(entity.getDefaultDescription());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    private PackageItemDTO mapToItemDTO(PackageItemEntity entity) {
        PackageItemDTO dto = new PackageItemDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setImageUrl(entity.getImageUrl());
        dto.setPackageCategoryId(entity.getPackageCategoryId());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    private void setErrorResponse(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

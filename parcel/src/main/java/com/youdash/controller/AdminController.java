package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminLoginDTO;
import com.youdash.dto.AdminResponseDTO;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PackageItemDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/login")
    public ApiResponse<AdminResponseDTO> login(@RequestBody AdminLoginDTO dto) {
        return adminService.login(dto);
    }

    // --- VEHICLE MANAGEMENT ---

    @PostMapping("/vehicles")
    public ApiResponse<VehicleDTO> createVehicle(@RequestBody VehicleDTO dto) {
        return adminService.createVehicle(dto);
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleDTO>> getAllVehicles() {
        return adminService.getAllVehicles();
    }

    @PutMapping("/vehicles/{id}")
    public ApiResponse<VehicleDTO> updateVehicle(@PathVariable Long id, @RequestBody VehicleDTO dto) {
        return adminService.updateVehicle(id, dto);
    }

    // --- CATEGORY MANAGEMENT ---

    @PostMapping("/categories")
    public ApiResponse<PackageCategoryDTO> createCategory(@RequestBody PackageCategoryDTO dto) {
        return adminService.createCategory(dto);
    }

    @GetMapping("/categories")
    public ApiResponse<List<PackageCategoryDTO>> getAllCategories() {
        return adminService.getAllCategories();
    }

    @GetMapping("/categories/active")
    public ApiResponse<List<PackageCategoryDTO>> getActiveCategories() {
        return adminService.getActiveCategories();
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<PackageCategoryDTO> updateCategory(@PathVariable Long id, @RequestBody PackageCategoryDTO dto) {
        return adminService.updateCategory(id, dto);
    }

    @PutMapping("/categories/{id}/toggle")
    public ApiResponse<PackageCategoryDTO> toggleCategory(@PathVariable Long id) {
        return adminService.toggleCategory(id);
    }

    // --- PACKAGE ITEM MANAGEMENT ---

    @PostMapping("/package-items")
    public ApiResponse<PackageItemDTO> createPackageItem(@RequestBody PackageItemDTO dto) {
        return adminService.createPackageItem(dto);
    }

    @GetMapping("/package-items")
    public ApiResponse<List<PackageItemDTO>> getAllPackageItems() {
        return adminService.getAllPackageItems();
    }

    @PutMapping("/package-items/{id}")
    public ApiResponse<PackageItemDTO> updatePackageItem(@PathVariable Long id, @RequestBody PackageItemDTO dto) {
        return adminService.updatePackageItem(id, dto);
    }

    @PutMapping("/package-items/{id}/toggle")
    public ApiResponse<PackageItemDTO> togglePackageItem(@PathVariable Long id) {
        return adminService.togglePackageItem(id);
    }
}

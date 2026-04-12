package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.service.AdminService;
import com.youdash.service.PackageCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public")
public class PublicController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private PackageCategoryService packageCategoryService;

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleDTO>> getActiveVehicles() {
        return adminService.getActiveVehicles();
    }

    @GetMapping("/package-categories")
    public ApiResponse<List<PackageCategoryDTO>> packageCategories() {
        return packageCategoryService.listActivePublic();
    }
}


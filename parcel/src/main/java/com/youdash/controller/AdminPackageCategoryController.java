package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.PackageCategoryRequestDTO;
import com.youdash.service.PackageCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/package-categories")
public class AdminPackageCategoryController {

    @Autowired
    private PackageCategoryService packageCategoryService;

    @GetMapping
    public ApiResponse<List<PackageCategoryDTO>> list() {
        return packageCategoryService.listAllAdmin();
    }

    @PostMapping
    public ApiResponse<PackageCategoryDTO> create(@RequestBody PackageCategoryRequestDTO dto) {
        return packageCategoryService.createAdmin(dto);
    }

    @PutMapping("/{id}")
    public ApiResponse<PackageCategoryDTO> update(@PathVariable Long id, @RequestBody PackageCategoryRequestDTO dto) {
        return packageCategoryService.updateAdmin(id, dto);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return packageCategoryService.deleteAdmin(id);
    }
}

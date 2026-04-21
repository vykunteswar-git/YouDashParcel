package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.BannerRequestDTO;
import com.youdash.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/banners")
@Tag(name = "Admin — Banners", description = "Manage home banners shown in user app.")
public class AdminBannerController {

    @Autowired
    private BannerService bannerService;

    @GetMapping
    @Operation(summary = "List all banners")
    public ApiResponse<List<BannerDTO>> list() {
        return bannerService.listAdmin();
    }

    @PostMapping
    @Operation(summary = "Create banner")
    public ApiResponse<BannerDTO> create(@RequestBody BannerRequestDTO dto) {
        return bannerService.createAdmin(dto);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update banner")
    public ApiResponse<BannerDTO> update(@PathVariable Long id, @RequestBody BannerRequestDTO dto) {
        return bannerService.updateAdmin(id, dto);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete banner")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return bannerService.deleteAdmin(id);
    }
}

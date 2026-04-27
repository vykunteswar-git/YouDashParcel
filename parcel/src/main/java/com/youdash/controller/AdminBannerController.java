package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.BannerRequestDTO;
import com.youdash.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public ApiResponse<BannerDTO> create(
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "subtitle", required = false) String subtitle,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "startsAt", required = false) String startsAt,
            @RequestParam(value = "endsAt", required = false) String endsAt,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        return bannerService.createAdmin(
                buildRequest(title, subtitle, imageUrl, redirectUrl, sortOrder, isActive, startsAt, endsAt),
                imageFile);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update banner")
    public ApiResponse<BannerDTO> update(
            @PathVariable Long id,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "subtitle", required = false) String subtitle,
            @RequestParam(value = "imageUrl", required = false) String imageUrl,
            @RequestParam(value = "redirectUrl", required = false) String redirectUrl,
            @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
            @RequestParam(value = "isActive", required = false) Boolean isActive,
            @RequestParam(value = "startsAt", required = false) String startsAt,
            @RequestParam(value = "endsAt", required = false) String endsAt,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile) {
        return bannerService.updateAdmin(
                id,
                buildRequest(title, subtitle, imageUrl, redirectUrl, sortOrder, isActive, startsAt, endsAt),
                imageFile);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete banner")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        return bannerService.deleteAdmin(id);
    }

    private BannerRequestDTO buildRequest(
            String title,
            String subtitle,
            String imageUrl,
            String redirectUrl,
            Integer sortOrder,
            Boolean isActive,
            String startsAt,
            String endsAt) {
        BannerRequestDTO dto = new BannerRequestDTO();
        dto.setTitle(title);
        dto.setSubtitle(subtitle);
        dto.setImageUrl(imageUrl);
        dto.setRedirectUrl(redirectUrl);
        dto.setSortOrder(sortOrder);
        dto.setIsActive(isActive);
        dto.setStartsAt(startsAt);
        dto.setEndsAt(endsAt);
        return dto;
    }
}

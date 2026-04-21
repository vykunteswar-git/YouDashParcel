package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.BannerDTO;
import com.youdash.dto.CheckoutPaymentOptionsDTO;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.service.AdminService;
import com.youdash.service.AppConfigService;
import com.youdash.service.BannerService;
import com.youdash.service.PackageCategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/public")
@Tag(name = "Public", description = "Public app bootstrapping data (no JWT).")
public class PublicController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private PackageCategoryService packageCategoryService;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private BannerService bannerService;

    @GetMapping("/vehicles")
    @Operation(summary = "List active vehicles")
    public ApiResponse<List<VehicleDTO>> getActiveVehicles() {
        return adminService.getActiveVehicles();
    }

    @GetMapping("/package-categories")
    @Operation(summary = "List active package categories")
    public ApiResponse<List<PackageCategoryDTO>> packageCategories() {
        return packageCategoryService.listActivePublic();
    }

    @GetMapping("/checkout-payment-options")
    @Operation(summary = "Get checkout payment options", description = "Used by user app to render COD/ONLINE selectors based on live admin config.")
    public ApiResponse<CheckoutPaymentOptionsDTO> checkoutPaymentOptions() {
        return appConfigService.getCheckoutPaymentOptions();
    }

    @GetMapping("/banners")
    @Operation(summary = "Get active user banners", description = "Returns active banners sorted by sortOrder for user-home carousel.")
    public ApiResponse<List<BannerDTO>> banners() {
        return bannerService.listPublicActive();
    }
}


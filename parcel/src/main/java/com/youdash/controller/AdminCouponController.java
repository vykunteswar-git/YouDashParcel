package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.coupon.CouponAdminResponseDTO;
import com.youdash.dto.coupon.CouponAdminUpsertDTO;
import com.youdash.service.CouponService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/admin/coupons")
@Tag(name = "Admin — Coupons", description = "Create and manage promo codes for the user app.")
public class AdminCouponController {

    @Autowired
    private CouponService couponService;

    @PostMapping
    @Operation(summary = "Create coupon")
    public ApiResponse<CouponAdminResponseDTO> create(@RequestBody CouponAdminUpsertDTO dto) {
        return couponService.adminCreate(dto);
    }

    @GetMapping
    @Operation(summary = "List all coupons")
    public ApiResponse<List<CouponAdminResponseDTO>> list() {
        return couponService.adminListAll();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update coupon")
    public ApiResponse<CouponAdminResponseDTO> update(
            @PathVariable long id,
            @RequestBody CouponAdminUpsertDTO dto) {
        return couponService.adminUpdate(id, dto);
    }
}

package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.coupon.PublicCouponDTO;
import com.youdash.service.CouponService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/coupons")
@Tag(name = "Coupons — User", description = "List active promo codes (USER JWT). Apply a code with couponCode on POST /orders or /orders/calculate-final.")
public class CouponController {

    @Autowired
    private CouponService couponService;

    @GetMapping("/active")
    @Operation(summary = "List active coupons for the app", description = "Requires a USER token (not rider/admin).")
    public ApiResponse<List<PublicCouponDTO>> active(@RequestAttribute(value = "type", required = false) String type) {
        if (!"USER".equals(type)) {
            ApiResponse<List<PublicCouponDTO>> denied = new ApiResponse<>();
            denied.setMessage("User token required");
            denied.setMessageKey("ERROR");
            denied.setSuccess(false);
            denied.setStatus(403);
            return denied;
        }
        return couponService.listActiveVisibleForUser();
    }
}

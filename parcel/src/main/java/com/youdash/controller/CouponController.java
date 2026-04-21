package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.coupon.CouponApplication;
import com.youdash.dto.coupon.CouponApplyRequestDTO;
import com.youdash.dto.coupon.CouponApplyResponseDTO;
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

    @PostMapping("/apply")
    @Operation(summary = "Validate/apply a coupon preview", description = "Validates coupon for current user and returns discount + payable amount. No redemption is recorded here.")
    public ApiResponse<CouponApplyResponseDTO> apply(
            @RequestBody CouponApplyRequestDTO dto,
            @RequestAttribute("userId") Long userId,
            @RequestAttribute(value = "type", required = false) String type) {
        ApiResponse<CouponApplyResponseDTO> response = new ApiResponse<>();
        try {
            if (!"USER".equals(type)) {
                throw new RuntimeException("User token required");
            }
            if (dto == null || dto.getCouponCode() == null || dto.getCouponCode().isBlank()) {
                throw new RuntimeException("couponCode is required");
            }
            if (dto.getServiceMode() == null) {
                throw new RuntimeException("serviceMode is required");
            }
            if (dto.getPreCouponTotal() == null || dto.getPreCouponTotal() <= 0) {
                throw new RuntimeException("preCouponTotal must be > 0");
            }
            CouponApplication application = couponService.resolveApplication(
                    userId,
                    dto.getCouponCode(),
                    dto.getPreCouponTotal(),
                    dto.getServiceMode());
            double discount = round2(application.discountAmount());
            double preCoupon = round2(dto.getPreCouponTotal());
            CouponApplyResponseDTO data = CouponApplyResponseDTO.builder()
                    .couponCode(application.normalizedCode())
                    .preCouponTotal(preCoupon)
                    .discountAmount(discount)
                    .payableTotal(round2(preCoupon - discount))
                    .build();
            response.setData(data);
            response.setMessage("Coupon applied");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

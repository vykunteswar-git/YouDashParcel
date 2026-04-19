package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.coupon.CouponAdminResponseDTO;
import com.youdash.dto.coupon.CouponAdminUpsertDTO;
import com.youdash.dto.coupon.CouponApplication;
import com.youdash.dto.coupon.PublicCouponDTO;
import com.youdash.model.ServiceMode;

public interface CouponService {

    ApiResponse<List<PublicCouponDTO>> listActiveVisibleForUser();

    CouponApplication resolveApplication(Long userId, String rawCode, double preCouponTotal, ServiceMode serviceMode);

    void recordRedemption(long couponId, long userId, long orderId);

    ApiResponse<CouponAdminResponseDTO> adminCreate(CouponAdminUpsertDTO dto);

    ApiResponse<CouponAdminResponseDTO> adminUpdate(long couponId, CouponAdminUpsertDTO dto);

    ApiResponse<List<CouponAdminResponseDTO>> adminListAll();
}

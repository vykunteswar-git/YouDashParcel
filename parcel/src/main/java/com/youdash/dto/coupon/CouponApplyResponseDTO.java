package com.youdash.dto.coupon;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponApplyResponseDTO {
    private String couponCode;
    private Double preCouponTotal;
    private Double discountAmount;
    private Double payableTotal;
}

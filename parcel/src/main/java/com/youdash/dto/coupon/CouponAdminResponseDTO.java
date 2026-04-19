package com.youdash.dto.coupon;

import com.youdash.model.CouponDiscountType;
import com.youdash.model.ServiceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponAdminResponseDTO {

    private Long id;
    private String code;
    private String title;
    private String description;
    private CouponDiscountType discountType;
    private Double discountValue;
    private Double maxDiscountAmount;
    private Double minOrderAmount;
    private String validFrom;
    private String validTo;
    private Integer maxRedemptionsTotal;
    private Integer redemptionCount;
    private Integer maxRedemptionsPerUser;
    private ServiceMode serviceMode;
    private Boolean active;
}

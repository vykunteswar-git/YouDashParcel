package com.youdash.dto.coupon;

import java.time.Instant;

import com.youdash.model.CouponDiscountType;
import com.youdash.model.ServiceMode;
import lombok.Data;

@Data
public class CouponAdminUpsertDTO {

    private String code;
    private String title;
    private String description;
    private CouponDiscountType discountType;
    private Double discountValue;
    private Double maxDiscountAmount;
    private Double minOrderAmount;
    private Instant validFrom;
    private Instant validTo;
    private Integer maxRedemptionsTotal;
    private Integer maxRedemptionsPerUser;
    private ServiceMode serviceMode;
    private Boolean active;
}

package com.youdash.dto.coupon;

import com.youdash.model.ServiceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PublicCouponDTO {

    private String code;
    private String title;
    private String description;
    /** Human-readable, e.g. "20% off" or "₹50 off". */
    private String discountSummary;
    private Double minOrderAmount;
    private ServiceMode serviceMode;
    private String validFrom;
    private String validTo;
}

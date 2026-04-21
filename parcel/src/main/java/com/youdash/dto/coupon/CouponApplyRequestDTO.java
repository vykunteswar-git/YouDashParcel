package com.youdash.dto.coupon;

import com.youdash.model.ServiceMode;
import lombok.Data;

@Data
public class CouponApplyRequestDTO {
    /** Coupon code typed by user (case-insensitive). */
    private String couponCode;
    /** INCITY or OUTSTATION. */
    private ServiceMode serviceMode;
    /**
     * Pre-coupon payable amount against which coupon rules are validated and discount is computed.
     * In current pricing, this is total before coupon (subtotal + gst + platformFee).
     */
    private Double preCouponTotal;
}

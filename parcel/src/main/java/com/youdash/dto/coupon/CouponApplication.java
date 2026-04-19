package com.youdash.dto.coupon;

/**
 * Result of validating a coupon against a pre-discount order total.
 */
public record CouponApplication(long couponId, String normalizedCode, double discountAmount) {}

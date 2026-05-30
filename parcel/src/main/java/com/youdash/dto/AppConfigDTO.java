package com.youdash.dto;

import com.youdash.model.PaymentType;
import lombok.Data;

import java.util.List;

@Data
public class AppConfigDTO {

    private Long id;
    private Double gstPercent;
    /** @deprecated Use {@link #incityPlatformFee} / {@link #outstationPlatformFee}. */
    private Double platformFee;
    private Double incityPlatformFee;
    private Double outstationPlatformFee;
    /** Legacy fallback ₹/km when no pickup tier matches. */
    private Double pickupRatePerKm;
    /** Legacy fallback ₹/km when no drop tier matches. */
    private Double dropRatePerKm;
    private Double perKgRate;
    private Double defaultRouteRatePerKm;
    private Boolean codEnabled;
    private Boolean onlineEnabled;
    private PaymentType defaultPaymentType;
    private List<OutstationLegRateTierDTO> pickupLegTiers;
    private List<OutstationLegRateTierDTO> dropLegTiers;
}

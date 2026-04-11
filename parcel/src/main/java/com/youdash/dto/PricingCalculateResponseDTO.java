package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Pricing line items and fees. Always include numeric fields in JSON (even 0) so clients never miss GST/platform fee.
 */
@Data
@JsonInclude(JsonInclude.Include.ALWAYS)
public class PricingCalculateResponseDTO {

    /** INCITY | OUTSTATION */
    private String serviceMode;
    /**
     * Primary pickup↔drop distance used in the quote (driving route km when Google Maps API key is configured;
     * otherwise great-circle / haversine km).
     */
    private Double straightLineDistanceKm;

    /** OUTSTATION: request delivery option; null for INCITY */
    private String deliveryOption;

    /** OUTSTATION leg amounts (after applying delivery option); null for INCITY */
    private Double firstMileCost;
    /** Line-haul between hubs (same as interHubComponent for outstation) */
    private Double hubRouteCost;
    private Double lastMileCost;

    /** OUTSTATION: resolved hubs used for pickup-side and drop-side legs */
    private Long originHubId;
    private Long destinationHubId;
    /** Route km: pickup → origin hub (0 if no pickup leg); driving when Maps key is set, else haversine */
    private Double firstMileDistanceKm;
    /** Route km: destination hub → drop (0 if no drop leg); driving when Maps key is set, else haversine */
    private Double lastMileDistanceKm;
    /** Rates from global config used for first / last mile (₹/km) */
    private Double firstMileRatePerKm;
    private Double lastMileRatePerKm;

    /** INCITY: same as vehicleComponent (UI-friendly). OUTSTATION: null. */
    private Double vehicleCost;
    /** Same as weightCharge (UI-friendly). */
    private Double weightCost;
    /** OUTSTATION: line-haul between hubs (same as interHubComponent). INCITY: null. */
    private Double hubTransportCost;

    private Double vehicleComponent;
    /** Deprecated for pricing: kept 0 — zone pricing is not used in fare calculation. */
    private Double zoneComponent;
    private Double firstMileComponent;
    private Double interHubComponent;
    private Double lastMileComponent;

    private Double weightCharge;

    private Double subTotalBeforeMin;
    private Double minimumChargeApplied;
    private Double subTotalAfterMin;

    private Double platformFee;
    private Double gstPercent;
    private Double gstAmount;
    private Double totalAmount;

    private List<Long> hubPathIds = new ArrayList<>();
    /** Hub cities (or names) along the outstation path; empty for incity */
    private List<String> route = new ArrayList<>();

    private List<String> breakdownLines = new ArrayList<>();
}

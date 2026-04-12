package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Always includes all top-level keys: use empty lists when not applicable (incity vs outstation).
 * {@code serviceType} is {@code INCITY} or {@code OUTSTATION}.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class QuoteResponseDTO {

    private String serviceType;
    private Double distanceKm;
    private List<VehicleOptionDTO> vehicleOptions;
    private List<HubOptionDTO> originHubs;
    private List<HubOptionDTO> destinationHubs;
    private List<String> deliveryTypes;

    /** OUTSTATION: UI copy + SLA per delivery mode; empty for INCITY */
    private List<DeliveryTypeDetailsDTO> deliveryTypeDetails;

    /**
     * OUTSTATION: true when exactly one of pickup/drop has hubs in range — use manual order request;
     * false when both sides have hubs, both empty, INCITY, or service fully unavailable.
     */
    private Boolean manualRequest;

    /** OUTSTATION: true when both pickup and drop have no hub within search radius */
    private Boolean serviceUnavailable;

    /** Shown when {@link #serviceUnavailable} is true */
    private String unavailableMessage;
}

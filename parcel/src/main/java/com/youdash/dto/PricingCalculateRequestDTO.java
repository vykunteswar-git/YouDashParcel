package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

@Data
@Schema(description = "Unified pricing request (incity + outstation). Coordinate requirements depend on deliveryOption; see API docs.")
public class PricingCalculateRequestDTO {

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(description = "Required for INCITY and for outstation modes that use pickup (e.g. DOOR_TO_DOOR, DOOR_TO_HUB). Omit for HUB_TO_HUB; optional for HUB_TO_DOOR if sourceHubId is sent.")
    private Double pickupLat;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double pickupLng;

    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    @Schema(description = "Required for INCITY and for outstation modes that use drop (e.g. DOOR_TO_DOOR, HUB_TO_DOOR). Omit for HUB_TO_HUB; optional for DOOR_TO_HUB if destinationHubId is sent.")
    private Double dropLat;

    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double dropLng;

    @Schema(description = "Required when service resolves to INCITY")
    private Long vehicleId;

    @Schema(description = "Parcel weight in kg (optional)")
    private Double weightKg;

    /**
     * Required when service resolves to OUTSTATION:
     * {@code DOOR_TO_DOOR}, {@code HUB_TO_HUB}, {@code DOOR_TO_HUB}, or {@code HUB_TO_DOOR}.
     */
    @Schema(description = "Outstation only: fulfillment / pricing mode")
    private String deliveryOption;

    @Schema(description = "Origin hub; required for HUB_TO_HUB; required for HUB_TO_DOOR if pickup coordinates are omitted")
    private Long sourceHubId;

    @Schema(description = "Destination hub; required for HUB_TO_HUB; required for DOOR_TO_HUB if drop coordinates are omitted")
    private Long destinationHubId;
}

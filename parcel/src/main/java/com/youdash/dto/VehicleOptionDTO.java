package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class VehicleOptionDTO {

    private Long vehicleId;
    private String name;

    /** Vehicle fare before GST and platform fee (same as previous {@code estimatedTotal} semantics). */
    private Double subtotal;

    /** Estimated amount to pay: subtotal + GST + platform fee. */
    private Double estimatedTotal;

    private Double maxWeight;

    /** From vehicle {@code pricePerKm}. */
    @JsonProperty("perKm")
    private Double perKm;

    /** GST percent from price config, e.g. 18. Serialized as {@code "gst"}. */
    @JsonProperty("gst")
    private Double gstPercent;

    private Double gstAmount;

    @JsonProperty("platformFee")
    private Double platformFee;
}

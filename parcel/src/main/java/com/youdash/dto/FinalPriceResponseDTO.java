package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.ALWAYS)
public class FinalPriceResponseDTO {

    private Double pickupDistanceKm;
    private Double hubDistanceKm;
    private Double dropDistanceKm;
    private Double pickupCost;
    private Double hubCost;
    private Double dropCost;
    private Double weightCost;
    private Double subtotal;
    private Double gstAmount;
    private Double platformFee;
    private Double total;
}

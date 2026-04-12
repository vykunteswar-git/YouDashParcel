package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryTypeDetailsDTO {

    private String type;
    private String title;
    private String description;
    private String handoverMessage;
    private DeliveryPromiseDTO deliveryPromise;

    /** Nearest origin / destination hub ids used for this quote row (same for all types in one quote). */
    private SelectedHubsDTO selectedHubs;

    /** OUTSTATION full quote: per-mode price; null when not computed */
    private DeliveryTypePricingDTO pricing;
}

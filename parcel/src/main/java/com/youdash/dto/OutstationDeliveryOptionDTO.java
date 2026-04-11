package com.youdash.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UI-facing outstation mode; {@code type} matches {@code FulfillmentType} / pricing {@code deliveryOption}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Outstation fulfillment choice for display and order submission")
public class OutstationDeliveryOptionDTO {

    @Schema(description = "DOOR_TO_DOOR | DOOR_TO_HUB | HUB_TO_DOOR")
    private String type;

    private String title;

    private String description;
}

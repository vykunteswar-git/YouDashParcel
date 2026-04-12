package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Default hubs used for quote pricing (nearest origin + nearest destination).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectedHubsDTO {

    private Long originHubId;
    private Long destinationHubId;
}

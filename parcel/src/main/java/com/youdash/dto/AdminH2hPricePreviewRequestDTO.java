package com.youdash.dto;

import lombok.Data;

@Data
public class AdminH2hPricePreviewRequestDTO {

    private Long originHubId;
    private Long destinationHubId;
    private Double weight;
}

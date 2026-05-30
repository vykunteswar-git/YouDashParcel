package com.youdash.dto;

import lombok.Data;

@Data
public class VerifyHubHandoverRequestDTO {
    /** {@code DROP} = sender deposit at origin hub (HUB_TO_DOOR). {@code COLLECT} = receiver pickup at destination hub (DOOR_TO_HUB). */
    private String type;
    private String otp;
    private Boolean adminOverride;
    /** Required for COD on DROP when amount not yet collected: {@code CASH} or {@code QR}. */
    private String codCollectionMode;
}

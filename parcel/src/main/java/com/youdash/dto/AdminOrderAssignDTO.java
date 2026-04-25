package com.youdash.dto;

import lombok.Data;

@Data
public class AdminOrderAssignDTO {

    private Long riderId;
    private Long pickupRiderId;
    private Long deliveryRiderId;
    private String assignmentRole;
}

package com.youdash.dto;

import lombok.Data;

@Data
public class WeightCostSlabDTO {
    private Long id;
    private Double minWeightKg;
    private Double maxWeightKg;
    private Double flatCost;
    private Integer sortOrder;
    private Boolean isActive;
}

package com.youdash.dto;

import com.youdash.model.OutstationLegType;
import lombok.Data;

@Data
public class OutstationLegRateTierDTO {

    private Long id;
    private OutstationLegType legType;
    private Double minWeightKg;
    private Double maxWeightKg;
    private Double ratePerKm;
    private Integer sortOrder;
    private Boolean isActive;
}

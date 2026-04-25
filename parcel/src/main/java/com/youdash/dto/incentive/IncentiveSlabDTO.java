package com.youdash.dto.incentive;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IncentiveSlabDTO {
    private Integer requiredDeliveries;
    private Double bonusAmount;
}

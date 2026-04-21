package com.youdash.dto.incentive;

import com.youdash.model.ServiceMode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PeakIncentiveCampaignDTO {
    private Long id;
    private String name;
    private String description;
    private ServiceMode serviceMode;
    private Double bonusAmount;
    private Integer minCompletedOrders;
    private Boolean isActive;
    private String validFrom;
    private String validTo;
    private List<String> daysOfWeek;
    private String startTimeHhmm;
    private String endTimeHhmm;
}

package com.youdash.dto.incentive;

import com.youdash.model.ServiceMode;
import com.youdash.model.IncentiveType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PeakIncentiveCampaignDTO {
    private Long id;
    private IncentiveType incentiveType;
    private String name;
    private String description;
    private ServiceMode serviceMode;
    private String incentiveDate; // yyyy-MM-dd
    private Integer targetOnlineMinutes;
    private Double bonusAmount;
    private Integer minCompletedOrders;
    private List<IncentiveSlabDTO> slabs;
    private Boolean isActive;
    private String validFrom;
    private String validTo;
    private List<String> daysOfWeek;
    private String startTimeHhmm;
    private String endTimeHhmm;
}

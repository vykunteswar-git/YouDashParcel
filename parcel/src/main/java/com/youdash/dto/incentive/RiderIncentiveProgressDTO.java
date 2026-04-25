package com.youdash.dto.incentive;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.youdash.model.ServiceMode;
import com.youdash.model.IncentiveType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RiderIncentiveProgressDTO {
    private Long campaignId;
    private IncentiveType incentiveType;
    private String campaignType; // string alias for clients
    private String incentiveTag; // HOURS | DELIVERIES
    private String campaignName;
    private ServiceMode serviceMode;
    private String incentiveDate;
    private Integer targetOnlineMinutes;
    private Integer completedOnlineMinutes;
    private Double bonusAmount;
    private Integer minCompletedOrders;
    private Long completedOrdersInWindow;
    private Integer remainingOrders;
    private Integer nextRequiredDeliveries;
    private Double nextBonusAmount;
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private List<IncentiveSlabDTO> slabs;
    private Boolean eligibleNow;
    private String windowStart;
    private String windowEnd;
    private String status; // UPCOMING | ACTIVE | CLOSED
}

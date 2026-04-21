package com.youdash.dto.incentive;

import com.youdash.model.ServiceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderIncentiveProgressDTO {
    private Long campaignId;
    private String campaignName;
    private ServiceMode serviceMode;
    private Double bonusAmount;
    private Integer minCompletedOrders;
    private Long completedOrdersInWindow;
    private Integer remainingOrders;
    private Boolean eligibleNow;
    private String windowStart;
    private String windowEnd;
    private String status; // UPCOMING | ACTIVE | CLOSED
}

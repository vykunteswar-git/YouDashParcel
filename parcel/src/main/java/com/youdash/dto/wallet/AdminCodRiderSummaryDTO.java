package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class AdminCodRiderSummaryDTO {
    private Long riderId;
    private String riderName;
    private String riderPhone;
    private String riderPublicId;
    private Double commissionPending;
    private Double handoverLimit;
    private String handoverStatus;
    private boolean dispatchBlocked;
    private long openLineCount;
    private Double lastDepositAmount;
    private String lastDepositAt;
}

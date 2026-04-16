package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class AdminWithdrawalApproveDTO {
    private Long withdrawalId;
    private Boolean approve;
}

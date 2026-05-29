package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class AdminCodDepositHistoryDTO {
    private Long depositId;
    private Double amount;
    private Long hubId;
    private String note;
    private String createdAt;
}

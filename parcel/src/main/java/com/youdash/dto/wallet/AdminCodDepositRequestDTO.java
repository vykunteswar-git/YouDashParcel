package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class AdminCodDepositRequestDTO {
    private Long riderId;
    private Double amount;
    private Long hubId;
    private String note;
}

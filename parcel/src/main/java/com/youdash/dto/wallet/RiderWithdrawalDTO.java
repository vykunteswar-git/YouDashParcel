package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderWithdrawalDTO {
    private Long id;
    private Double amount;
    private String status;
    private String accountHolderName;
    private String accountNumber;
    private String ifsc;
    private String createdAt;
}

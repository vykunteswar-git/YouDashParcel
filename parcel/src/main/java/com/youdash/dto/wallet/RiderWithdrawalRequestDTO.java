package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderWithdrawalRequestDTO {
    private Double amount;
    private String accountHolderName;
    private String accountNumber;
    private String ifsc;
}

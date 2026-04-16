package com.youdash.dto.wallet;

import lombok.Data;

@Data
public class RiderWalletTransactionDTO {
    private Long id;
    private String type;
    private Double amount;
    private String referenceType;
    private Long referenceId;
    private String status;
    private String note;
    private String metadataJson;
    private String createdAt;
}

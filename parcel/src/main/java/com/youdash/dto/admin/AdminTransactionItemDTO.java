package com.youdash.dto.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTransactionItemDTO {
    private String txnId;
    private String sourceType; // ORDER_PAY | PAYOUT
    private Long sourceId;
    private String partyType; // USER | RIDER
    private Long partyId;
    private String partyName;
    private String method; // ONLINE | COD | BANK_TRANSFER
    private String status;
    private Double amount;
    private String createdAt;
    private String reference;
}

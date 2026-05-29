package com.youdash.dto.wallet;

import java.util.List;

import lombok.Data;

@Data
public class AdminCodRiderDetailDTO {
    private AdminCodRiderSummaryDTO summary;
    private List<AdminCodOpenLineDTO> openLines;
    private List<AdminCodDepositHistoryDTO> recentDeposits;
}

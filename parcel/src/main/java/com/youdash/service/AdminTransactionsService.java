package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminTransactionItemDTO;
import com.youdash.dto.admin.AdminTransactionSummaryDTO;

import java.util.List;

public interface AdminTransactionsService {
    ApiResponse<AdminTransactionSummaryDTO> getSummary(String from, String to);

    ApiResponse<List<AdminTransactionItemDTO>> listTransactions(
            String type,
            String status,
            String q,
            String from,
            String to,
            int page,
            int size);
}

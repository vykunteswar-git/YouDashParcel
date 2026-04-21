package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminTransactionItemDTO;
import com.youdash.dto.admin.AdminTransactionSummaryDTO;
import com.youdash.service.AdminTransactionsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/transactions")
@Tag(name = "Admin — Transactions", description = "Financial transaction summary + list for admin dashboard.")
public class AdminTransactionsController {

    @Autowired
    private AdminTransactionsService adminTransactionsService;

    @GetMapping("/summary")
    @Operation(summary = "Transactions summary cards")
    public ApiResponse<AdminTransactionSummaryDTO> summary(
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return adminTransactionsService.getSummary(from, to);
    }

    @GetMapping
    @Operation(summary = "List transactions", description = "type=ALL|ORDER_PAY|PAYOUT, status optional, q matches txnId/party/reference.")
    public ApiResponse<List<AdminTransactionItemDTO>> list(
            @RequestParam(name = "type", required = false, defaultValue = "ALL") String type,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return adminTransactionsService.listTransactions(type, status, q, from, to, page, size);
    }
}

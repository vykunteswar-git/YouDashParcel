package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.wallet.RiderWalletSummaryDTO;
import com.youdash.dto.wallet.RiderWalletTransactionDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;
import com.youdash.dto.wallet.RiderWithdrawalRequestDTO;
import com.youdash.security.RiderAccessVerifier;
import com.youdash.service.OrderService;
import com.youdash.service.wallet.RiderWalletService;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

@RestController
@RequestMapping("/rider")
public class RiderWalletController {

    @Autowired
    private RiderWalletService riderWalletService;

    @Autowired
    private RiderAccessVerifier riderAccessVerifier;

    @Autowired
    private OrderService orderService;

    @GetMapping("/wallet")
    public ApiResponse<RiderWalletSummaryDTO> wallet(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.getWalletSummary(riderId);
    }

    @GetMapping("/transactions")
    public ApiResponse<List<RiderWalletTransactionDTO>> transactions(
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.listTransactions(riderId, page, size);
    }

    @PostMapping("/withdraw")
    public ApiResponse<RiderWithdrawalDTO> withdraw(@RequestBody RiderWithdrawalRequestDTO dto, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.requestWithdrawal(riderId, dto);
    }

    @GetMapping("/withdrawals")
    public ApiResponse<List<RiderWithdrawalDTO>> withdrawals(
            HttpServletRequest request,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderWalletService.listWithdrawals(riderId, page, size);
    }

    @GetMapping("/orders")
    public ApiResponse<List<OrderResponseDTO>> myOrders(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return orderService.listRiderOrders(riderId);
    }
}

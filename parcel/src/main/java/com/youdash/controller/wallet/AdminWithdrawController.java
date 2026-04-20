package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.wallet.AdminWithdrawalApproveDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;
import com.youdash.service.wallet.RiderWalletService;

import java.util.List;

@RestController
@RequestMapping("/admin/withdraw")
public class AdminWithdrawController {

    @Autowired
    private RiderWalletService riderWalletService;

    @GetMapping("/requests")
    public ApiResponse<List<RiderWithdrawalDTO>> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "50") int size) {
        return riderWalletService.adminListWithdrawals(status, page, size);
    }

    @PostMapping("/approve")
    public ApiResponse<RiderWithdrawalDTO> approve(
            @RequestBody AdminWithdrawalApproveDTO dto,
            @RequestAttribute("userId") Long adminUserId) {
        return riderWalletService.adminApproveWithdrawal(adminUserId, dto);
    }
}

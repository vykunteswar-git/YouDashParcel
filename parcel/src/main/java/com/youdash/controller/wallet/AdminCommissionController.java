package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.wallet.RiderCommissionConfigDTO;
import com.youdash.service.wallet.RiderWalletService;

@RestController
@RequestMapping("/admin/commission")
public class AdminCommissionController {

    @Autowired
    private RiderWalletService riderWalletService;

    @GetMapping("/config")
    public ApiResponse<RiderCommissionConfigDTO> get() {
        return riderWalletService.getCommissionConfig();
    }

    @PostMapping("/config")
    public ApiResponse<RiderCommissionConfigDTO> upsert(
            @RequestBody RiderCommissionConfigDTO dto,
            @RequestAttribute("userId") Long adminUserId) {
        return riderWalletService.upsertCommissionConfig(adminUserId, dto);
    }
}

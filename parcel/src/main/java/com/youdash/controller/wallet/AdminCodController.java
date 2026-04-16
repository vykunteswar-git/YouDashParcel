package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.wallet.AdminCodSettleRequestDTO;
import com.youdash.service.wallet.RiderWalletService;

@RestController
@RequestMapping("/admin/cod")
public class AdminCodController {

    @Autowired
    private RiderWalletService riderWalletService;

    @PostMapping("/settle")
    public ApiResponse<String> settle(
            @RequestBody AdminCodSettleRequestDTO dto,
            @RequestAttribute("userId") Long adminUserId) {
        return riderWalletService.adminSettleCod(adminUserId, dto);
    }
}

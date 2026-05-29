package com.youdash.controller.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.wallet.AdminCodDepositRequestDTO;
import com.youdash.dto.wallet.AdminCodHandoverLimitRequestDTO;
import com.youdash.dto.wallet.AdminCodRiderDetailDTO;
import com.youdash.dto.wallet.AdminCodRiderSummaryDTO;
import com.youdash.dto.wallet.AdminCodSettleRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.service.wallet.RiderWalletService;

import java.util.List;

@RestController
@RequestMapping("/admin/cod")
public class AdminCodController {

    @Autowired
    private RiderWalletService riderWalletService;

    @GetMapping("/riders")
    public ApiResponse<List<AdminCodRiderSummaryDTO>> listRiders(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search) {
        return riderWalletService.adminListCodRiders(status, search);
    }

    @GetMapping("/riders/{riderId}")
    public ApiResponse<AdminCodRiderDetailDTO> riderDetail(@PathVariable Long riderId) {
        return riderWalletService.adminGetCodRiderDetail(riderId);
    }

    @PostMapping("/deposit")
    public ApiResponse<String> confirmDeposit(
            @RequestBody AdminCodDepositRequestDTO dto,
            @RequestAttribute("userId") Long adminUserId) {
        return riderWalletService.adminConfirmCodDeposit(adminUserId, dto);
    }

    @PatchMapping("/riders/{riderId}/handover-limit")
    public ApiResponse<RiderResponseDTO> updateHandoverLimit(
            @PathVariable Long riderId,
            @RequestBody AdminCodHandoverLimitRequestDTO dto) {
        return riderWalletService.adminUpdateCodHandoverLimit(riderId, dto);
    }

    /** Legacy per-order settle (commission amount). Prefer POST /deposit for bulk handover. */
    @PostMapping("/settle")
    public ApiResponse<String> settle(
            @RequestBody AdminCodSettleRequestDTO dto,
            @RequestAttribute("userId") Long adminUserId) {
        return riderWalletService.adminSettleCod(adminUserId, dto);
    }
}

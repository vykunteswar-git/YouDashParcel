package com.youdash.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.youdash.dto.wallet.RiderWalletTransactionDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;

import lombok.Data;

@Data
public class RiderResponseDTO {
    /** Set when registration succeeds; send on subsequent requests in the Authorization Bearer header. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;

    private Long id;
    private String publicId;
    private String name;
    private String phone;
    private String email;
    private Long vehicleId;
    private String vehicleType;
    private String vehicleNumber;
    private Boolean isAvailable;
    private Boolean isBlocked;
    private Double rating;
    private String approvalStatus;
    private String emergencyPhone;
    private Double currentLat;
    private Double currentLng;
    private String fcmToken;
    private String profileImageUrl;
    private String aadhaarImageUrl;
    private String licenseImageUrl;

    private Long totalOrdersDelivered;
    private Double walletCurrentBalance;
    private Double walletTotalEarnings;
    private Double walletTotalWithdrawn;
    private Double walletCodPendingAmount;
    private Double walletWithdrawalPendingAmount;
    private Double walletNetAvailable;

    private List<RiderWalletTransactionDTO> recentWalletTransactions;
    private List<RiderWithdrawalDTO> recentWithdrawals;
    private List<OrderResponseDTO> recentOrders;
}

package com.youdash.dto;

import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;
import com.youdash.model.wallet.CodSettlementStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponseDTO {

    private Long id;
    private Long userId;
    private Long categoryId;
    private String senderName;
    private String senderPhone;
    private String receiverName;
    private String receiverPhone;
    private String imageUrl;
    private Double pickupLat;
    private Double pickupLng;
    private Double dropLat;
    private Double dropLng;
    private ServiceMode serviceMode;
    private Long vehicleId;
    private String deliveryType;
    private Long originHubId;
    private Long destinationHubId;
    private Double weight;
    private Double distanceKm;
    private PaymentType paymentType;
    private OrderStatus status;
    private Long riderId;
    /** Populated when {@code riderId} is set (rider profile name / phone). */
    private String riderName;
    private String riderPhone;
    /** Shown in {@code IN_TRANSIT} until OTP is verified; then cleared in API responses. */
    private String deliveryOtp;
    private Boolean isOtpVerified;
    private Double subtotal;
    private Double gstAmount;
    private Double platformFee;
    /** Payable amount after coupon (pre-coupon total minus couponAmount). */
    private Double totalAmount;
    private Double couponAmount;
    private Double vehiclePricePerKm;
    /** Public reference for payments (e.g. YP-…). */
    private String displayOrderId;
    private String paymentStatus;
    private String razorpayOrderId;
    private Double codCollectedAmount;
    private CodCollectionMode codCollectionMode;
    private CodSettlementStatus codSettlementStatus;
    private String createdAt;
}

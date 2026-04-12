package com.youdash.dto;

import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderResponseDTO {

    private Long id;
    private Long userId;
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
    private PaymentType paymentType;
    private OrderStatus status;
    private Long riderId;
    private Double subtotal;
    private Double gstAmount;
    private Double platformFee;
    /** Payable amount after coupon (pre-coupon total minus couponAmount). */
    private Double totalAmount;
    private Double couponAmount;
    private String createdAt;
}

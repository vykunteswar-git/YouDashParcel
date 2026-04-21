package com.youdash.dto;

import com.youdash.model.PaymentType;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CheckoutPaymentOptionsDTO {
    private Boolean codEnabled;
    private Boolean onlineEnabled;
    private PaymentType defaultPaymentType;
    private List<PaymentType> availablePaymentTypes;
}

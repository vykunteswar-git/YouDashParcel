package com.youdash.dto;

import lombok.Data;

@Data
public class OrderResponseDTO {

    private Long id;
    private String orderId;
    private String status;
    private Double totalAmount;
    private String imageUrl;
}

package com.youdash.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeliveryOptionsResponseDTO {

    private List<String> incityOptions;
    private List<String> outstationOptions;
}

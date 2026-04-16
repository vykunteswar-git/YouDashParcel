package com.youdash.dto;

import lombok.Data;

@Data
public class RiderAuthResponseDTO {
    private String token;
    private RiderResponseDTO rider;
}


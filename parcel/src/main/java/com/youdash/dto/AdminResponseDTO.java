package com.youdash.dto;

import lombok.Data;

@Data
public class AdminResponseDTO {
    private Long id;
    private String email;
    private String token;
}

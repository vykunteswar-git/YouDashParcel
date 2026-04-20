package com.youdash.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AdminRevenueTrendPointDTO {
    private String label;
    private Double revenue;
    private Long orders;
}


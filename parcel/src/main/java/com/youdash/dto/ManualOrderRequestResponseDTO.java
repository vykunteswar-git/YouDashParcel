package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ManualOrderRequestResponseDTO {

    private Long id;
    private String status;
}

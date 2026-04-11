package com.youdash.dto;

import com.youdash.model.DeliveryOptionCategory;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DeliveryOptionAdminResponseDTO {

    private Long id;
    private DeliveryOptionCategory category;
    private String code;
    private Integer sortOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

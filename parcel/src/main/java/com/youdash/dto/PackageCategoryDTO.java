package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PackageCategoryDTO {

    private Long id;
    private String name;
    private String emoji;
    private Integer sortOrder;
    private Boolean isActive;
}

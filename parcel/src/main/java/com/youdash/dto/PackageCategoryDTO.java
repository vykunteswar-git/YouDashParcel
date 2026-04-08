package com.youdash.dto;

import lombok.Data;

@Data
public class PackageCategoryDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private String defaultDescription;
    private Boolean isActive;
}

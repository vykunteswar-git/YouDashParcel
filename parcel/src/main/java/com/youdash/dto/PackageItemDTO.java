package com.youdash.dto;

import lombok.Data;

@Data
public class PackageItemDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private Long packageCategoryId;
    private Boolean isActive;
}

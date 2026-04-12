package com.youdash.dto;

import lombok.Data;

@Data
public class PackageCategoryRequestDTO {

    /** Display name, e.g. "Documents" */
    private String name;

    /** Emoji character(s), e.g. "📄" */
    private String emoji;

    /** Optional; lower sorts first. */
    private Integer sortOrder;

    /** Optional; default true on create. */
    private Boolean isActive;
}

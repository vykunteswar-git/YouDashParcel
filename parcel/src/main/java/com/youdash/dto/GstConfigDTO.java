package com.youdash.dto;

import lombok.Data;

@Data
public class GstConfigDTO {
    // New preferred single GST percent (total)
    private Double gstPercent;

    // Legacy fields (still accepted for backward compatibility)
    private Double cgstPercent;
    private Double sgstPercent;
}


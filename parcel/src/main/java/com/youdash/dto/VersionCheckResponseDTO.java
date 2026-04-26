package com.youdash.dto;

import lombok.Data;

@Data
public class VersionCheckResponseDTO {
    private Boolean updateRequired;
    private String playStoreUrl;
}

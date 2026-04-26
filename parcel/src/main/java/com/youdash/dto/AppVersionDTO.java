package com.youdash.dto;

import lombok.Data;

@Data
public class AppVersionDTO {
    private Long id;
    private Integer userVersionCode;
    private String userPlayStoreUrl;
    private Integer riderVersionCode;
    private String riderPlayStoreUrl;
}

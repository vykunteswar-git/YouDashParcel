package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BannerDTO {
    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String redirectUrl;
    private Integer sortOrder;
    private Boolean isActive;
    private String startsAt;
    private String endsAt;
}

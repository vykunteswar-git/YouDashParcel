package com.youdash.dto;

import lombok.Data;

/**
 * For admin create/update, image can be sent as multipart file.
 * imageUrl remains as optional fallback for backward compatibility.
 */
@Data
public class BannerRequestDTO {
    private String title;
    private String subtitle;
    private String imageUrl;
    private String redirectUrl;
    private Integer sortOrder;
    private Boolean isActive;
    /** ISO-8601 UTC timestamp string (optional). */
    private String startsAt;
    /** ISO-8601 UTC timestamp string (optional). */
    private String endsAt;
}

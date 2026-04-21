package com.youdash.dto;

import lombok.Data;

/**
 * Image upload is expected to be done externally (S3/Cloudinary/Firebase Storage).
 * Pass final imageUrl here to publish banner metadata.
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

package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public links consumed by mobile clients.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppLinksResponse {
    private String privacyPolicy;
    private String termsAndConditions;
}

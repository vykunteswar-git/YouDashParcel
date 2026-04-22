package com.youdash.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RiderOnlineTimeDTO {
    private String date;
    private Long totalOnlineSeconds;
    private Long totalOnlineMinutes;
    private String formatted;
    private String activeSessionStartedAt;
}

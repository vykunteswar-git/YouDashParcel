package com.youdash.dto.notification;

import lombok.Data;

import java.util.List;

@Data
public class AdminNotificationTargetsDTO {
    private List<String> cities;
    private List<AdminNotificationZoneOptionDTO> zones;
    private List<AdminNotificationTargetOptionDTO> users;
    private List<AdminNotificationTargetOptionDTO> riders;
}


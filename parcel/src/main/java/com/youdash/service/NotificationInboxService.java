package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.notification.NotificationInboxItemDTO;
import com.youdash.dto.notification.NotificationUnreadCountDTO;
import com.youdash.notification.NotificationType;

import java.util.List;
import java.util.Map;

public interface NotificationInboxService {
    void recordForUser(Long userId, String title, String body, Map<String, String> data, NotificationType type);

    void recordForRider(Long riderId, String title, String body, Map<String, String> data, NotificationType type);

    ApiResponse<List<NotificationInboxItemDTO>> listForUser(Long userId, int page, int size);

    ApiResponse<List<NotificationInboxItemDTO>> listForRider(Long riderId, int page, int size);

    ApiResponse<String> markReadForUser(Long userId, Long notificationId);

    ApiResponse<String> markReadForRider(Long riderId, Long notificationId);

    ApiResponse<String> markAllReadForUser(Long userId);

    ApiResponse<String> markAllReadForRider(Long riderId);

    ApiResponse<NotificationUnreadCountDTO> unreadCountForUser(Long userId);

    ApiResponse<NotificationUnreadCountDTO> unreadCountForRider(Long riderId);
}

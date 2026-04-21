package com.youdash.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.notification.NotificationInboxItemDTO;
import com.youdash.dto.notification.NotificationUnreadCountDTO;
import com.youdash.entity.notification.NotificationInboxEntity;
import com.youdash.model.notification.NotificationRecipientType;
import com.youdash.notification.NotificationType;
import com.youdash.repository.notification.NotificationInboxRepository;
import com.youdash.service.NotificationInboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class NotificationInboxServiceImpl implements NotificationInboxService {

    @Autowired
    private NotificationInboxRepository notificationInboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public void recordForUser(Long userId, String title, String body, Map<String, String> data, NotificationType type) {
        record(NotificationRecipientType.USER, userId, title, body, data, type);
    }

    @Override
    @Transactional
    public void recordForRider(Long riderId, String title, String body, Map<String, String> data, NotificationType type) {
        record(NotificationRecipientType.RIDER, riderId, title, body, data, type);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<NotificationInboxItemDTO>> listForUser(Long userId, int page, int size) {
        return list(NotificationRecipientType.USER, userId, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<NotificationInboxItemDTO>> listForRider(Long riderId, int page, int size) {
        return list(NotificationRecipientType.RIDER, riderId, page, size);
    }

    @Override
    @Transactional
    public ApiResponse<String> markReadForUser(Long userId, Long notificationId) {
        return markRead(NotificationRecipientType.USER, userId, notificationId);
    }

    @Override
    @Transactional
    public ApiResponse<String> markReadForRider(Long riderId, Long notificationId) {
        return markRead(NotificationRecipientType.RIDER, riderId, notificationId);
    }

    @Override
    @Transactional
    public ApiResponse<String> markAllReadForUser(Long userId) {
        return markAllRead(NotificationRecipientType.USER, userId);
    }

    @Override
    @Transactional
    public ApiResponse<String> markAllReadForRider(Long riderId) {
        return markAllRead(NotificationRecipientType.RIDER, riderId);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<NotificationUnreadCountDTO> unreadCountForUser(Long userId) {
        return unreadCount(NotificationRecipientType.USER, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<NotificationUnreadCountDTO> unreadCountForRider(Long riderId) {
        return unreadCount(NotificationRecipientType.RIDER, riderId);
    }

    private void record(NotificationRecipientType recipientType, Long recipientId, String title, String body, Map<String, String> data, NotificationType type) {
        if (recipientId == null || title == null || title.isBlank() || body == null || body.isBlank()) {
            return;
        }
        NotificationInboxEntity e = new NotificationInboxEntity();
        e.setRecipientType(recipientType);
        e.setRecipientId(recipientId);
        e.setTitle(title.trim());
        e.setBody(body.trim());
        e.setNotificationType(type);
        e.setDataJson(writeJson(data));
        e.setIsRead(false);
        notificationInboxRepository.save(e);
    }

    private ApiResponse<List<NotificationInboxItemDTO>> list(NotificationRecipientType recipientType, Long recipientId, int page, int size) {
        ApiResponse<List<NotificationInboxItemDTO>> response = new ApiResponse<>();
        try {
            if (recipientId == null) {
                throw new RuntimeException("recipientId is required");
            }
            var pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 200));
            List<NotificationInboxItemDTO> list = notificationInboxRepository
                    .findByRecipientTypeAndRecipientIdOrderByCreatedAtDesc(recipientType, recipientId, pageable)
                    .stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setTotalCount(list.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    private ApiResponse<String> markRead(NotificationRecipientType recipientType, Long recipientId, Long notificationId) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (recipientId == null || notificationId == null) {
                throw new RuntimeException("notificationId is required");
            }
            NotificationInboxEntity e = notificationInboxRepository
                    .findByIdAndRecipientTypeAndRecipientId(notificationId, recipientType, recipientId)
                    .orElseThrow(() -> new RuntimeException("Notification not found"));
            if (!Boolean.TRUE.equals(e.getIsRead())) {
                e.setIsRead(true);
                e.setReadAt(Instant.now());
                notificationInboxRepository.save(e);
            }
            response.setData("OK");
            response.setMessage("Notification marked as read");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setErr(response, ex.getMessage());
        }
        return response;
    }

    private ApiResponse<String> markAllRead(NotificationRecipientType recipientType, Long recipientId) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (recipientId == null) {
                throw new RuntimeException("recipientId is required");
            }
            notificationInboxRepository.markAllRead(recipientType, recipientId, Instant.now());
            response.setData("OK");
            response.setMessage("All notifications marked as read");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setErr(response, ex.getMessage());
        }
        return response;
    }

    private ApiResponse<NotificationUnreadCountDTO> unreadCount(NotificationRecipientType recipientType, Long recipientId) {
        ApiResponse<NotificationUnreadCountDTO> response = new ApiResponse<>();
        try {
            if (recipientId == null) {
                throw new RuntimeException("recipientId is required");
            }
            long c = notificationInboxRepository.countByRecipientTypeAndRecipientIdAndIsReadFalse(recipientType, recipientId);
            response.setData(new NotificationUnreadCountDTO(c));
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErr(response, e.getMessage());
        }
        return response;
    }

    private NotificationInboxItemDTO toDto(NotificationInboxEntity e) {
        return NotificationInboxItemDTO.builder()
                .id(e.getId())
                .title(e.getTitle())
                .body(e.getBody())
                .notificationType(e.getNotificationType() == null ? null : e.getNotificationType().name())
                .dataJson(e.getDataJson())
                .isRead(e.getIsRead())
                .readAt(e.getReadAt() == null ? null : e.getReadAt().toString())
                .createdAt(e.getCreatedAt() == null ? null : e.getCreatedAt().toString())
                .build();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private static void setErr(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

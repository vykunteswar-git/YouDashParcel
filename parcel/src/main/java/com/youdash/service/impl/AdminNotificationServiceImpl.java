package com.youdash.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.notification.AdminNotificationBroadcastRequestDTO;
import com.youdash.dto.notification.AdminNotificationCampaignDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.UserEntity;
import com.youdash.entity.ZoneEntity;
import com.youdash.entity.notification.AdminNotificationCampaignEntity;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.model.notification.AdminNotificationCampaignStatus;
import com.youdash.model.notification.AdminNotificationTargetType;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.repository.notification.AdminNotificationCampaignRepository;
import com.youdash.service.AdminNotificationService;
import com.youdash.service.NotificationService;
import com.youdash.service.ZoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminNotificationServiceImpl implements AdminNotificationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AdminNotificationCampaignRepository campaignRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    @Transactional
    public ApiResponse<AdminNotificationCampaignDTO> sendBroadcast(Long adminUserId, AdminNotificationBroadcastRequestDTO dto) {
        ApiResponse<AdminNotificationCampaignDTO> response = new ApiResponse<>();
        try {
            if (dto == null) {
                throw new RuntimeException("body is required");
            }
            if (!StringUtils.hasText(dto.getTargetType())) {
                throw new RuntimeException("targetType is required");
            }
            if (!StringUtils.hasText(dto.getTitle())) {
                throw new RuntimeException("title is required");
            }
            if (!StringUtils.hasText(dto.getBody())) {
                throw new RuntimeException("body is required");
            }
            AdminNotificationTargetType targetType = AdminNotificationTargetType.valueOf(dto.getTargetType().trim().toUpperCase(Locale.ROOT));
            ResolvedRecipients recipients = resolveRecipients(targetType, dto);

            AdminNotificationCampaignEntity c = new AdminNotificationCampaignEntity();
            c.setTitle(dto.getTitle().trim());
            c.setBody(dto.getBody().trim());
            c.setNotificationType(trimToNull(dto.getNotificationType()));
            c.setTargetType(targetType);
            c.setCity(trimToNull(dto.getCity()));
            c.setZoneId(dto.getZoneId());
            c.setUserIdsJson(writeJson(dto.getUserIds()));
            c.setRiderIdsJson(writeJson(dto.getRiderIds()));
            c.setDataJson(writeJson(dto.getData()));
            c.setCreatedBy(adminUserId);
            c.setTotalTargets(recipients.tokens().size());

            boolean saveDraft = Boolean.TRUE.equals(dto.getSaveDraft());
            if (saveDraft) {
                c.setStatus(AdminNotificationCampaignStatus.DRAFT);
                c.setSuccessCount(0);
                c.setFailedCount(0);
                c = campaignRepository.save(c);
                response.setData(toCampaignDto(c));
                response.setMessage("Draft saved");
                response.setMessageKey("SUCCESS");
                response.setSuccess(true);
                response.setStatus(200);
                return response;
            }

            int success = 0;
            int failed = 0;
            Map<String, String> payload = new HashMap<>();
            if (dto.getData() != null) {
                dto.getData().forEach((k, v) -> {
                    if (k != null && v != null) {
                        payload.put(k, v);
                    }
                });
            }
            if (StringUtils.hasText(dto.getNotificationType())) {
                payload.put("broadcastType", dto.getNotificationType().trim());
            }
            payload.put("targetType", targetType.name());

            for (String token : recipients.tokens()) {
                try {
                    notificationService.sendToToken(token, dto.getTitle().trim(), dto.getBody().trim(), payload, NotificationType.ADMIN_BROADCAST);
                    success++;
                } catch (Exception ignored) {
                    failed++;
                }
            }
            c.setSuccessCount(success);
            c.setFailedCount(failed);
            c.setStatus(success > 0 ? AdminNotificationCampaignStatus.SENT : AdminNotificationCampaignStatus.FAILED);
            c.setSentAt(Instant.now());
            c = campaignRepository.save(c);

            response.setData(toCampaignDto(c));
            response.setMessage("Broadcast queued");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (IllegalArgumentException e) {
            response.setMessage("Invalid targetType");
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(400);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<List<AdminNotificationCampaignDTO>> listCampaigns(int page, int size) {
        ApiResponse<List<AdminNotificationCampaignDTO>> response = new ApiResponse<>();
        try {
            var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200), Sort.by(Sort.Direction.DESC, "createdAt"));
            List<AdminNotificationCampaignDTO> list = campaignRepository.findAllByOrderByCreatedAtDesc(pageable)
                    .stream()
                    .map(this::toCampaignDto)
                    .toList();
            response.setData(list);
            response.setTotalCount(list.size());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    private ResolvedRecipients resolveRecipients(AdminNotificationTargetType targetType, AdminNotificationBroadcastRequestDTO dto) {
        return switch (targetType) {
            case ALL_USERS -> resolveAllUsers();
            case ALL_RIDERS -> resolveAllRiders();
            case SPECIFIC_USERS -> resolveSpecificUsers(dto.getUserIds());
            case SPECIFIC_RIDERS -> resolveSpecificRiders(dto.getRiderIds());
            case CITY_USERS -> resolveCityUsers(dto.getCity());
            case CITY_RIDERS -> resolveCityRiders(dto.getCity());
            case ZONE_USERS -> resolveZoneUsers(dto.getZoneId());
            case ZONE_RIDERS -> resolveZoneRiders(dto.getZoneId());
        };
    }

    private ResolvedRecipients resolveAllUsers() {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (UserEntity u : userRepository.findByActiveTrue()) {
            if (StringUtils.hasText(u.getFcmToken())) {
                tokens.add(u.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private ResolvedRecipients resolveAllRiders() {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (RiderEntity r : riderRepository.findByApprovalStatusOrderByCreatedAtDesc(RiderApprovalStatus.APPROVED)) {
            if (Boolean.TRUE.equals(r.getIsBlocked())) {
                continue;
            }
            if (StringUtils.hasText(r.getFcmToken())) {
                tokens.add(r.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private ResolvedRecipients resolveSpecificUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new RuntimeException("userIds are required for SPECIFIC_USERS");
        }
        Set<Long> unique = new LinkedHashSet<>(userIds);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (UserEntity u : userRepository.findAllById(unique)) {
            if (!Boolean.TRUE.equals(u.getActive())) {
                continue;
            }
            if (StringUtils.hasText(u.getFcmToken())) {
                tokens.add(u.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private ResolvedRecipients resolveSpecificRiders(List<Long> riderIds) {
        if (riderIds == null || riderIds.isEmpty()) {
            throw new RuntimeException("riderIds are required for SPECIFIC_RIDERS");
        }
        Set<Long> unique = new LinkedHashSet<>(riderIds);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (RiderEntity r : riderRepository.findAllById(unique)) {
            if (!RiderApprovalStatus.APPROVED.equalsIgnoreCase(nz(r.getApprovalStatus())) || Boolean.TRUE.equals(r.getIsBlocked())) {
                continue;
            }
            if (StringUtils.hasText(r.getFcmToken())) {
                tokens.add(r.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private ResolvedRecipients resolveCityRiders(String city) {
        if (!StringUtils.hasText(city)) {
            throw new RuntimeException("city is required for CITY_RIDERS");
        }
        String wanted = city.trim();
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (RiderEntity r : riderRepository.findByApprovalStatusOrderByCreatedAtDesc(RiderApprovalStatus.APPROVED)) {
            if (Boolean.TRUE.equals(r.getIsBlocked())) {
                continue;
            }
            if (r.getCurrentLat() == null || r.getCurrentLng() == null) {
                continue;
            }
            ZoneEntity zone = zoneService.findZoneContaining(r.getCurrentLat(), r.getCurrentLng()).orElse(null);
            if (zone == null || !equalsIgnoreCase(zone.getCity(), wanted)) {
                continue;
            }
            if (StringUtils.hasText(r.getFcmToken())) {
                tokens.add(r.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private ResolvedRecipients resolveZoneRiders(Long zoneId) {
        if (zoneId == null) {
            throw new RuntimeException("zoneId is required for ZONE_RIDERS");
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        for (RiderEntity r : riderRepository.findByApprovalStatusOrderByCreatedAtDesc(RiderApprovalStatus.APPROVED)) {
            if (Boolean.TRUE.equals(r.getIsBlocked())) {
                continue;
            }
            if (r.getCurrentLat() == null || r.getCurrentLng() == null) {
                continue;
            }
            ZoneEntity zone = zoneService.findZoneContaining(r.getCurrentLat(), r.getCurrentLng()).orElse(null);
            if (zone == null || !Objects.equals(zone.getId(), zoneId)) {
                continue;
            }
            if (StringUtils.hasText(r.getFcmToken())) {
                tokens.add(r.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private ResolvedRecipients resolveCityUsers(String city) {
        if (!StringUtils.hasText(city)) {
            throw new RuntimeException("city is required for CITY_USERS");
        }
        String wanted = city.trim();
        Set<Long> userIds = new LinkedHashSet<>();
        for (Map.Entry<Long, LatLng> e : latestUserLocations().entrySet()) {
            ZoneEntity zone = zoneService.findZoneContaining(e.getValue().lat(), e.getValue().lng()).orElse(null);
            if (zone != null && equalsIgnoreCase(zone.getCity(), wanted)) {
                userIds.add(e.getKey());
            }
        }
        return resolveUsersByIds(userIds);
    }

    private ResolvedRecipients resolveZoneUsers(Long zoneId) {
        if (zoneId == null) {
            throw new RuntimeException("zoneId is required for ZONE_USERS");
        }
        Set<Long> userIds = new LinkedHashSet<>();
        for (Map.Entry<Long, LatLng> e : latestUserLocations().entrySet()) {
            ZoneEntity zone = zoneService.findZoneContaining(e.getValue().lat(), e.getValue().lng()).orElse(null);
            if (zone != null && Objects.equals(zone.getId(), zoneId)) {
                userIds.add(e.getKey());
            }
        }
        return resolveUsersByIds(userIds);
    }

    private ResolvedRecipients resolveUsersByIds(Set<Long> userIds) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        if (userIds == null || userIds.isEmpty()) {
            return new ResolvedRecipients(tokens);
        }
        for (UserEntity u : userRepository.findAllById(userIds)) {
            if (!Boolean.TRUE.equals(u.getActive())) {
                continue;
            }
            if (StringUtils.hasText(u.getFcmToken())) {
                tokens.add(u.getFcmToken().trim());
            }
        }
        return new ResolvedRecipients(tokens);
    }

    private Map<Long, LatLng> latestUserLocations() {
        List<OrderEntity> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        Map<Long, LatLng> out = new HashMap<>();
        for (OrderEntity o : orders) {
            if (o.getUserId() == null || out.containsKey(o.getUserId())) {
                continue;
            }
            if (o.getPickupLat() != null && o.getPickupLng() != null) {
                out.put(o.getUserId(), new LatLng(o.getPickupLat(), o.getPickupLng()));
            } else if (o.getDropLat() != null && o.getDropLng() != null) {
                out.put(o.getUserId(), new LatLng(o.getDropLat(), o.getDropLng()));
            }
        }
        return out;
    }

    private AdminNotificationCampaignDTO toCampaignDto(AdminNotificationCampaignEntity e) {
        AdminNotificationCampaignDTO d = new AdminNotificationCampaignDTO();
        d.setId(e.getId());
        d.setTitle(e.getTitle());
        d.setBody(e.getBody());
        d.setNotificationType(e.getNotificationType());
        d.setTargetType(e.getTargetType() != null ? e.getTargetType().name() : null);
        d.setCity(e.getCity());
        d.setZoneId(e.getZoneId());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setTotalTargets(e.getTotalTargets());
        d.setSuccessCount(e.getSuccessCount());
        d.setFailedCount(e.getFailedCount());
        d.setCreatedBy(e.getCreatedBy());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        d.setSentAt(e.getSentAt() != null ? e.getSentAt().toString() : null);
        return d;
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

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static boolean equalsIgnoreCase(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private record ResolvedRecipients(LinkedHashSet<String> tokens) {
    }

    private record LatLng(double lat, double lng) {
    }
}


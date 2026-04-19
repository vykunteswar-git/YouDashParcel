package com.youdash.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.notification.NotificationType;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.service.NotificationService;

import lombok.Data;

@RestController
@RequestMapping("/admin/fcm-test")
public class AdminFcmTestController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private NotificationService notificationService;

    /** Returns all users and riders so admin can pick a target for test push. */
    @GetMapping("/targets")
    public ApiResponse<List<FcmTarget>> getTargets() {
        List<FcmTarget> targets = new ArrayList<>();

        userRepository.findAll().forEach(u -> {
            FcmTarget t = new FcmTarget();
            t.setId(u.getId());
            t.setType("USER");
            t.setName((u.getFirstName() != null ? u.getFirstName() : "") +
                    (u.getLastName() != null ? " " + u.getLastName() : ""));
            t.setPhone(u.getPhoneNumber());
            t.setFcmToken(u.getFcmToken());
            t.setHasFcmToken(StringUtils.hasText(u.getFcmToken()));
            targets.add(t);
        });

        riderRepository.findAll().forEach(r -> {
            FcmTarget t = new FcmTarget();
            t.setId(r.getId());
            t.setType("RIDER");
            t.setName(r.getName());
            t.setPhone(r.getPhone());
            t.setFcmToken(r.getFcmToken());
            t.setHasFcmToken(StringUtils.hasText(r.getFcmToken()));
            targets.add(t);
        });

        ApiResponse<List<FcmTarget>> res = new ApiResponse<>();
        res.setSuccess(true);
        res.setData(targets);
        res.setTotalCount(targets.size());
        return res;
    }

    /** Sends a test push to the provided FCM token with the given title and body. */
    @PostMapping("/send")
    public ApiResponse<String> sendTestPush(@RequestBody SendPushRequest request) {
        ApiResponse<String> res = new ApiResponse<>();

        if (!StringUtils.hasText(request.getFcmToken())) {
            res.setSuccess(false);
            res.setMessage("fcmToken is required");
            return res;
        }
        if (!StringUtils.hasText(request.getTitle())) {
            res.setSuccess(false);
            res.setMessage("title is required");
            return res;
        }

        String body = StringUtils.hasText(request.getBody()) ? request.getBody() : "";

        notificationService.sendToToken(
                request.getFcmToken(),
                request.getTitle(),
                body,
                null,
                NotificationType.USER_ORDER_STATUS_UPDATE);

        res.setSuccess(true);
        res.setMessage("Push notification sent to token: " + request.getFcmToken());
        return res;
    }

    // ── Inner DTOs ──────────────────────────────────────────────────────────────

    @Data
    public static class FcmTarget {
        private Long id;
        private String type;       // "USER" or "RIDER"
        private String name;
        private String phone;
        private String fcmToken;
        private boolean hasFcmToken;
    }

    @Data
    public static class SendPushRequest {
        private String fcmToken;
        private String title;
        private String body;
    }
}

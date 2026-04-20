package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.notification.AdminNotificationBroadcastRequestDTO;
import com.youdash.dto.notification.AdminNotificationCampaignDTO;
import com.youdash.service.AdminNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/notifications")
public class AdminNotificationController {

    @Autowired
    private AdminNotificationService adminNotificationService;

    @PostMapping("/broadcast")
    public ApiResponse<AdminNotificationCampaignDTO> broadcast(
            @RequestBody AdminNotificationBroadcastRequestDTO dto,
            @RequestAttribute("userId") Long adminUserId) {
        return adminNotificationService.sendBroadcast(adminUserId, dto);
    }

    @GetMapping("/logs")
    public ApiResponse<List<AdminNotificationCampaignDTO>> logs(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return adminNotificationService.listCampaigns(page, size);
    }
}


package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminLoginDTO;
import com.youdash.dto.AdminResponseDTO;
import com.youdash.dto.TestPushRequestDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.notification.NotificationType;
import com.youdash.service.NotificationService;
import com.youdash.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private NotificationService notificationService;

    @PostMapping("/login")
    public ApiResponse<AdminResponseDTO> login(@RequestBody AdminLoginDTO dto) {
        return adminService.login(dto);
    }

    @PostMapping("/vehicles")
    public ApiResponse<VehicleDTO> createVehicle(@RequestBody VehicleDTO dto) {
        return adminService.createVehicle(dto);
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleDTO>> getAllVehicles() {
        return adminService.getAllVehicles();
    }

    @PutMapping("/vehicles/{id}")
    public ApiResponse<VehicleDTO> updateVehicle(@PathVariable Long id, @RequestBody VehicleDTO dto) {
        return adminService.updateVehicle(id, dto);
    }

    /**
     * Admin-only utility to test Firebase push delivery using a device FCM token.
     * Requires Firebase Admin SDK service account to be configured on the server.
     */
    @PostMapping("/notifications/test")
    public ApiResponse<String> testPush(@RequestBody TestPushRequestDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getToken() == null || dto.getToken().trim().isEmpty()) {
                throw new RuntimeException("token is required");
            }
            String title = dto.getTitle() == null ? "Test notification" : dto.getTitle();
            String body = dto.getBody() == null ? "Hello from YouDash" : dto.getBody();
            NotificationType type = null;
            if (dto.getType() != null && !dto.getType().trim().isEmpty()) {
                type = NotificationType.valueOf(dto.getType().trim().toUpperCase());
            }

            notificationService.sendToToken(dto.getToken().trim(), title, body, dto.getData(), type);

            response.setData("SENT");
            response.setMessage("Push queued");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
        }
        return response;
    }
}

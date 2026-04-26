package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.UserResponseDTO;
import com.youdash.service.AdminService;
import com.youdash.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @Autowired
    private AdminService adminService;

    @GetMapping
    public ApiResponse<List<UserResponseDTO>> getAllUsersForAdmin() {
        return userService.getAllUsers();
    }

    @Operation(
        summary = "Hard-delete a user (permanent). Requires ADMIN token.",
        description = "Permanently removes the user row. "
            + "Fails with 500 if FK-constrained rows (orders, wallet) exist — soft-delete via DELETE /users/{id} instead."
    )
    @DeleteMapping("/{id}/hard-delete")
    public ApiResponse<String> hardDeleteUser(
            @PathVariable Long id,
            @RequestAttribute(value = "type", required = false) String type) {
        if (!"ADMIN".equals(type)) {
            ApiResponse<String> denied = new ApiResponse<>();
            denied.setMessage("Admin token required");
            denied.setMessageKey("ERROR");
            denied.setSuccess(false);
            denied.setStatus(403);
            return denied;
        }
        return adminService.hardDeleteUser(id);
    }
}


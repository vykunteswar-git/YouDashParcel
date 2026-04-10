package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.UserResponseDTO;
import com.youdash.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/users")
public class AdminUserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public ApiResponse<List<UserResponseDTO>> getAllUsersForAdmin() {
        return userService.getAllUsers();
    }
}


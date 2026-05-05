package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OtpRequestDTO;
import com.youdash.dto.OtpResponseDTO;
import com.youdash.dto.VerifyOtpRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.service.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/send-otp")
    public ApiResponse<OtpResponseDTO> sendOtp(@RequestBody OtpRequestDTO request) {
        return authService.sendOtp(request);
    }

    @PostMapping("/verify-otp")
    public ApiResponse<UserResponseDTO> verifyOtp(@RequestBody VerifyOtpRequestDTO request) {
        return authService.verifyOtp(request);
    }

    /**
     * Client should delete the stored JWT after this call. Stateless server — no server-side session to destroy.
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        ApiResponse<Void> r = new ApiResponse<>();
        r.setSuccess(true);
        r.setMessage("Logged out");
        r.setMessageKey("SUCCESS");
        r.setStatus(200);
        return r;
    }
}
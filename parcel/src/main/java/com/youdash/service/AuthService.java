package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OtpRequestDTO;
import com.youdash.dto.OtpResponseDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.dto.VerifyOtpRequestDTO;

public interface AuthService {

    ApiResponse<OtpResponseDTO> sendOtp(OtpRequestDTO request);

    ApiResponse<UserResponseDTO> verifyOtp(VerifyOtpRequestDTO request);
}
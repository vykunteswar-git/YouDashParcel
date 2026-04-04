package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OtpRequestDTO;
import com.youdash.dto.OtpResponseDTO;
import com.youdash.dto.OtpVerifyDTO;
import com.youdash.dto.UserResponseDTO;

public interface AuthService {

    ApiResponse<OtpResponseDTO> sendOtp(OtpRequestDTO request);

    ApiResponse<UserResponseDTO> verifyOtp(OtpVerifyDTO request);
}
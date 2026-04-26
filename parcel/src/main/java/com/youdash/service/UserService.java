package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FcmTokenRequestDTO;
import com.youdash.dto.UserRequestDTO;
import com.youdash.dto.UserResponseDTO;

public interface UserService {

    ApiResponse<UserResponseDTO> createUser(UserRequestDTO request);

    ApiResponse<List<UserResponseDTO>> getAllUsers();

    ApiResponse<UserResponseDTO> getUserById(Long id);

    ApiResponse<UserResponseDTO> findUserByPhoneNumber(String phoneNumber);

    ApiResponse<UserResponseDTO> updateUser(Long id, UserRequestDTO request);

    ApiResponse<String> deleteUser(Long id);

    /** Permanently delete the authenticated user's own account. */
    ApiResponse<String> closeAccount(Long userId);

    /** Persist FCM registration token for the authenticated customer app. */
    ApiResponse<String> saveFcmToken(Long userId, FcmTokenRequestDTO dto);
}
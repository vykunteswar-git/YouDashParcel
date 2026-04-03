package com.youdash.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.UserRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.entity.UserEntity;
import com.youdash.repository.UserRepository;
import com.youdash.service.UserService;

@Service
public class UserServiceImpl implements UserService {

  @Autowired
  private UserRepository userRepository;

  // CREATE USER
  @Override
  public ApiResponse<UserResponseDTO> createUser(UserRequestDTO request) {

    ApiResponse<UserResponseDTO> response = new ApiResponse<>();

    try {

      UserEntity user = new UserEntity();
      user.setPhoneNumber(request.getPhoneNumber());
      user.setFirstName(request.getFirstName());
      user.setLastName(request.getLastName());
      user.setEmail(request.getEmail());

      user.setActive(true);
      user.setProfileCompleted(false);

      UserEntity savedUser = userRepository.save(user);

      response.setData(mapToDTO(savedUser));
      response.setMessage("User created successfully");
      response.setMessageKey("SUCCESS");
      response.setStatus(200);
      response.setSuccess(true);

    } catch (Exception e) {
      response.setMessage("User creation failed");
      response.setMessageKey("ERROR");
      response.setStatus(500);
      response.setSuccess(false);
    }

    return response;
  }

  // GET ALL USERS
  @Override
  public ApiResponse<List<UserResponseDTO>> getAllUsers() {

    ApiResponse<List<UserResponseDTO>> response = new ApiResponse<>();

    try {
      List<UserResponseDTO> users = userRepository.findByActiveTrue()
          .stream()
          .map(this::mapToDTO)
          .collect(Collectors.toList());

      response.setData(users);
      response.setMessage("Users fetched successfully");
      response.setMessageKey("SUCCESS");
      response.setStatus(200);
      response.setTotalCount(users.size());
      response.setSuccess(true);

    } catch (Exception e) {
      response.setMessage("Failed to fetch users");
      response.setMessageKey("ERROR");
      response.setStatus(500);
      response.setSuccess(false);
    }

    return response;
  }

  // GET USER BY ID
  @Override
  public ApiResponse<UserResponseDTO> getUserById(Long id) {

    ApiResponse<UserResponseDTO> response = new ApiResponse<>();

    try {

      if (id == null) {
        throw new IllegalArgumentException("User ID cannot be null");
      }

      UserEntity user = userRepository.findById(id)
          .filter(UserEntity::getActive)
          .orElseThrow(() -> new RuntimeException("User not found"));

      response.setData(mapToDTO(user));
      response.setMessage("User fetched successfully");
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

  // GET USER BY PHONE
  @Override
  public ApiResponse<UserResponseDTO> findUserByPhoneNumber(String phoneNumber) {

    ApiResponse<UserResponseDTO> response = new ApiResponse<>();

    try {
      UserEntity user = userRepository.findByPhoneNumber(phoneNumber)
          .filter(UserEntity::getActive)
          .orElseThrow(() -> new RuntimeException("User not found"));

      response.setData(mapToDTO(user));
      response.setMessage("User fetched successfully");
      response.setMessageKey("SUCCESS");
      response.setStatus(200);
      response.setSuccess(true);

    } catch (Exception e) {
      response.setMessage("User not found");
      response.setMessageKey("NOT_FOUND");
      response.setStatus(404);
      response.setSuccess(false);
    }

    return response;
  }

  // UPDATE USER
  @Override
  public ApiResponse<UserResponseDTO> updateUser(Long id, UserRequestDTO request) {

    ApiResponse<UserResponseDTO> response = new ApiResponse<>();

    try {
      if (id == null) {
        throw new IllegalArgumentException("User ID cannot be null");
      }
      UserEntity user = userRepository.findById(id)
          .filter(UserEntity::getActive)
          .orElseThrow(() -> new RuntimeException("User not found"));

      // ✅ Only update non-null fields
      if (request.getPhoneNumber() != null) {
        user.setPhoneNumber(request.getPhoneNumber());
      }

      if (request.getFirstName() != null) {
        user.setFirstName(request.getFirstName());
      }

      if (request.getLastName() != null) {
        user.setLastName(request.getLastName());
      }

      if (request.getEmail() != null) {
        user.setEmail(request.getEmail());
      }

      // ✅ Profile completion logic
      if (user.getFirstName() != null &&
          user.getLastName() != null &&
          user.getEmail() != null) {

        user.setProfileCompleted(true);
      }

      userRepository.save(user);

      response.setData(mapToDTO(user));
      response.setMessage("User updated successfully");
      response.setMessageKey("SUCCESS");
      response.setStatus(200);
      response.setSuccess(true);

    } catch (Exception e) {
      response.setMessage("User update failed");
      response.setMessageKey("ERROR");
      response.setStatus(500);
      response.setSuccess(false);
    }

    return response;
  }

  // SOFT DELETE
  @Override
  public ApiResponse<String> deleteUser(Long id) {

    ApiResponse<String> response = new ApiResponse<>();

    try {

      if (id == null) {
        throw new IllegalArgumentException("User ID cannot be null");
      }

      UserEntity user = userRepository.findById(id)
          .filter(UserEntity::getActive)
          .orElseThrow(() -> new RuntimeException("User not found"));

      user.setActive(false);
      userRepository.save(user);

      response.setMessage("User deactivated successfully");
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

  // 🔁 MAPPING METHOD
  private UserResponseDTO mapToDTO(UserEntity user) {

    UserResponseDTO dto = new UserResponseDTO();

    dto.setId(user.getId());
    dto.setPhoneNumber(user.getPhoneNumber());
    dto.setFirstName(user.getFirstName());
    dto.setLastName(user.getLastName());
    dto.setEmail(user.getEmail());
    dto.setActive(user.getActive());
    dto.setProfileCompleted(user.getProfileCompleted());

    return dto;
  }
}
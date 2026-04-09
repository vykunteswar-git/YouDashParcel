package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FcmTokenRequestDTO;
import com.youdash.dto.UserRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.entity.UserEntity;
import com.youdash.repository.UserRepository;
import com.youdash.service.UserService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/users")
public class UserController {

  @Autowired
  private UserService userService;

  @Autowired
  private UserRepository userRepository;

  @PostMapping("/fcm-token")
  public ApiResponse<String> saveFcmToken(@RequestBody FcmTokenRequestDTO dto, HttpServletRequest request) {
    ApiResponse<String> response = new ApiResponse<>();
    try {
      Object idAttr = request.getAttribute("userId");
      if (idAttr == null) {
        throw new RuntimeException("Unauthorized");
      }

      Long userId = Long.valueOf(idAttr.toString());
      if (dto == null || dto.getToken() == null || dto.getToken().trim().isEmpty()) {
        throw new RuntimeException("FCM token is required");
      }

      UserEntity user = userRepository.findById(userId)
          .filter(u -> Boolean.TRUE.equals(u.getActive()))
          .orElseThrow(() -> new RuntimeException("User not found"));

      user.setFcmToken(dto.getToken().trim());
      userRepository.save(user);

      response.setData("Token saved");
      response.setMessage("FCM token saved successfully");
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

  // CREATE
  @PostMapping
  public ApiResponse<UserResponseDTO> createUser(@RequestBody UserRequestDTO request) {
    return userService.createUser(request);
  }

  // GET ALL
  @GetMapping
  public ApiResponse<?> getAllUsers() {
    return userService.getAllUsers();
  }

  // GET BY ID
  @GetMapping("/{id}")
  public ApiResponse<UserResponseDTO> getUser(@PathVariable Long id) {
    return userService.getUserById(id);
  }

  // GET BY PHONE
  @GetMapping("/phone/{phone}")
  public ApiResponse<UserResponseDTO> getUserByPhone(@PathVariable String phone) {
    return userService.findUserByPhoneNumber(phone);
  }

  // UPDATE
  @PutMapping("/{id}")
  public ApiResponse<UserResponseDTO> updateUser(@PathVariable Long id,
      @RequestBody UserRequestDTO request) {
    return userService.updateUser(id, request);
  }

  // DELETE (SOFT)
  @DeleteMapping("/{id}")
  public ApiResponse<String> deleteUser(@PathVariable Long id) {
    return userService.deleteUser(id);
  }
}
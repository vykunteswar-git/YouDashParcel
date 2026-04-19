package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FcmTokenRequestDTO;
import com.youdash.dto.UserRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

  @Autowired
  private UserService userService;

  /** Customer app: refresh FCM token without re-running OTP (Bearer USER JWT). */
  @PostMapping("/me/fcm-token")
  public ApiResponse<String> saveMyFcmToken(
      @RequestBody FcmTokenRequestDTO dto,
      @RequestAttribute("userId") Long userId,
      @RequestAttribute(value = "type", required = false) String type) {
    if (!"USER".equals(type)) {
      ApiResponse<String> denied = new ApiResponse<>();
      denied.setSuccess(false);
      denied.setMessage("User token required");
      denied.setMessageKey("ERROR");
      denied.setStatus(403);
      return denied;
    }
    return userService.saveFcmToken(userId, dto);
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

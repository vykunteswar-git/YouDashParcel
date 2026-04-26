package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FcmTokenRequestDTO;
import com.youdash.dto.UserRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.dto.notification.NotificationInboxItemDTO;
import com.youdash.dto.notification.NotificationUnreadCountDTO;
import com.youdash.service.NotificationInboxService;
import com.youdash.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

  @Autowired
  private UserService userService;

  @Autowired
  private NotificationInboxService notificationInboxService;

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

  @GetMapping("/me/notifications")
  public ApiResponse<java.util.List<NotificationInboxItemDTO>> myNotifications(
      @RequestAttribute("userId") Long userId,
      @RequestAttribute(value = "type", required = false) String type,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "50") int size) {
    if (!"USER".equals(type)) {
      ApiResponse<java.util.List<NotificationInboxItemDTO>> denied = new ApiResponse<>();
      denied.setSuccess(false);
      denied.setMessage("User token required");
      denied.setMessageKey("ERROR");
      denied.setStatus(403);
      return denied;
    }
    return notificationInboxService.listForUser(userId, page, size);
  }

  @GetMapping("/me/notifications/unread-count")
  public ApiResponse<NotificationUnreadCountDTO> myUnreadCount(
      @RequestAttribute("userId") Long userId,
      @RequestAttribute(value = "type", required = false) String type) {
    if (!"USER".equals(type)) {
      ApiResponse<NotificationUnreadCountDTO> denied = new ApiResponse<>();
      denied.setSuccess(false);
      denied.setMessage("User token required");
      denied.setMessageKey("ERROR");
      denied.setStatus(403);
      return denied;
    }
    return notificationInboxService.unreadCountForUser(userId);
  }

  @PostMapping("/me/notifications/{id}/read")
  public ApiResponse<String> markMyNotificationRead(
      @PathVariable("id") Long id,
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
    return notificationInboxService.markReadForUser(userId, id);
  }

  @PostMapping("/me/notifications/read-all")
  public ApiResponse<String> markMyNotificationsReadAll(
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
    return notificationInboxService.markAllReadForUser(userId);
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

  // HARD DELETE (self — authenticated user closes their own account)
  @DeleteMapping("/me/close-account")
  public ApiResponse<String> closeAccount(
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
    return userService.closeAccount(userId);
  }
}

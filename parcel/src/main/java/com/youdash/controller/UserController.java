package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.UserRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.service.UserService;

@RestController
@RequestMapping("/users")
public class UserController {

  @Autowired
  private UserService userService;

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
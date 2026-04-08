package com.youdash.service.impl;

import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OtpRequestDTO;
import com.youdash.dto.OtpResponseDTO;
import com.youdash.dto.OtpVerifyDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.entity.OtpEntity;
import com.youdash.entity.UserEntity;
import com.youdash.repository.OtpRepository;
import com.youdash.repository.UserRepository;
import com.youdash.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

  @Autowired
  private OtpRepository otpRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private com.youdash.util.JwtUtil jwtUtil;

  // SEND OTP
  @Override
  public ApiResponse<OtpResponseDTO> sendOtp(OtpRequestDTO request) {

    ApiResponse<OtpResponseDTO> response = new ApiResponse<>();

    try {
      String phone = request.getPhoneNumber();

      // 🔥 Dummy OTP (for testing)
      String otp = phone.equals("9999999999") ? "1234"
          : String.valueOf(new Random().nextInt(9000) + 1000);

      OtpEntity otpEntity = otpRepository.findByPhoneNumber(phone)
          .orElse(new OtpEntity());

      otpEntity.setPhoneNumber(phone);
      otpEntity.setOtp(otp);
      otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(5));

      otpRepository.save(otpEntity);

      // 👉 MSG91 integration here (later)

      System.out.println("OTP for " + phone + " = " + otp);

      response.setData(new OtpResponseDTO(phone, otp)); // 🔥 Return phone + OTP for frontend testing
      response.setMessage("OTP sent successfully");
      response.setMessageKey("SUCCESS");
      response.setStatus(200);
      response.setSuccess(true);

    } catch (Exception e) {
      response.setMessage("Failed to send OTP");
      response.setSuccess(false);
    }

    return response;
  }

  // VERIFY OTP
  @Override
  public ApiResponse<UserResponseDTO> verifyOtp(OtpVerifyDTO request) {

    ApiResponse<UserResponseDTO> response = new ApiResponse<>();

    try {
      System.out.println("Verifying OTP for " + request.getPhoneNumber() + " (Entered: " + request.getOtp() + ")");
      
      OtpEntity otpEntity = otpRepository
          .findTopByPhoneNumberOrderByIdDesc(request.getPhoneNumber())
          .orElseThrow(() -> new RuntimeException("OTP not found"));

      // Expiry check
      if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now())) {
        throw new RuntimeException("OTP expired");
      }

      // OTP match
      if (!otpEntity.getOtp().equals(request.getOtp())) {
        throw new RuntimeException("Invalid OTP");
      }

      // Check user exists
      UserEntity user = userRepository.findByPhoneNumber(request.getPhoneNumber())
          .map(existingUser -> {
            // ✅ Reactivate user if they were soft-deleted
            if (Boolean.FALSE.equals(existingUser.getActive())) {
              existingUser.setActive(true);
              return userRepository.save(existingUser);
            }
            return existingUser;
          })
          .orElseGet(() -> {
            UserEntity newUser = new UserEntity();
            newUser.setPhoneNumber(request.getPhoneNumber());
            newUser.setActive(true);
            newUser.setProfileCompleted(false);
            return userRepository.save(newUser);
          });

      String token = jwtUtil.generateToken(user.getId(), "USER");
      UserResponseDTO userDTO = mapToDTO(user);
      userDTO.setToken(token);

      response.setData(userDTO);
      response.setMessage("Login successful");
      response.setSuccess(true);
      response.setStatus(200);

    } catch (Exception e) {
      response.setMessage(e.getMessage());
      response.setSuccess(false);
      response.setStatus(400);
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
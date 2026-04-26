package com.youdash.service.impl;

import java.time.LocalDateTime;
import java.util.Random;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OtpRequestDTO;
import com.youdash.dto.OtpResponseDTO;
import com.youdash.dto.VerifyOtpRequestDTO;
import com.youdash.dto.UserResponseDTO;
import com.youdash.entity.OtpEntity;
import com.youdash.entity.UserEntity;
import com.youdash.repository.OtpRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.service.AuthService;

@Service
public class AuthServiceImpl implements AuthService {

  @Value("${auth.test-login.enabled:false}")
  private boolean testLoginEnabled;

  @Value("${auth.test-login.phone:}")
  private String testLoginPhone;

  @Value("${auth.test-login.otp:1234}")
  private String testLoginOtp;

  @Autowired
  private OtpRepository otpRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RiderRepository riderRepository;

  @Autowired
  private com.youdash.util.JwtUtil jwtUtil;

  // SEND OTP
  @Override
  public ApiResponse<OtpResponseDTO> sendOtp(OtpRequestDTO request) {
    ApiResponse<OtpResponseDTO> response = new ApiResponse<>();
    try {
      String phone = request.getPhoneNumber();
      boolean isTestPhone = testLoginEnabled
          && testLoginPhone != null
          && !testLoginPhone.isBlank()
          && testLoginPhone.trim().equals(phone);

      String otp = isTestPhone
          ? testLoginOtp
          : String.valueOf(new Random().nextInt(9000) + 1000);

      OtpEntity otpEntity = otpRepository.findByPhoneNumber(phone)
          .orElse(new OtpEntity());
      otpEntity.setPhoneNumber(phone);
      otpEntity.setOtp(otp);
      otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(5));
      otpRepository.save(otpEntity);

      System.out.println("OTP for " + phone + " = " + otp);

      // Return OTP only for test phone; real phones get null otp in response.
      String responseOtp = isTestPhone ? otp : null;
      response.setData(new OtpResponseDTO(phone, responseOtp));
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
  public ApiResponse<UserResponseDTO> verifyOtp(VerifyOtpRequestDTO request) {
    ApiResponse<UserResponseDTO> response = new ApiResponse<>();
    try {
      System.out.println("Verifying OTP for " + request.getPhoneNumber() + " (Entered: " + request.getOtp() + ")");

      String phone = request.getPhoneNumber();
      boolean isTestPhone = testLoginEnabled
          && testLoginPhone != null
          && !testLoginPhone.isBlank()
          && testLoginPhone.trim().equals(phone);

      if (isTestPhone && testLoginOtp.equals(request.getOtp())) {
        // Test-login fast path: skip DB OTP lookup and expiry check.
      } else {
        OtpEntity otpEntity = otpRepository
            .findTopByPhoneNumberOrderByIdDesc(phone)
            .orElseThrow(() -> new RuntimeException("OTP not found"));

        if (otpEntity.getExpiryTime().isBefore(LocalDateTime.now())) {
          throw new RuntimeException("OTP expired");
        }
        if (!otpEntity.getOtp().equals(request.getOtp())) {
          throw new RuntimeException("Invalid OTP");
        }
      }

      UserEntity user = userRepository.findByPhoneNumber(phone)
          .map(existingUser -> {
            if (Boolean.FALSE.equals(existingUser.getActive())) {
              existingUser.setActive(true);
              return userRepository.save(existingUser);
            }
            return existingUser;
          })
          .orElseGet(() -> {
            UserEntity newUser = new UserEntity();
            newUser.setPhoneNumber(phone);
            newUser.setActive(true);
            newUser.setProfileCompleted(false);
            return userRepository.save(newUser);
          });

      String fcm = request.getFcmToken();
      if (fcm != null && !fcm.trim().isEmpty()) {
        String trimmed = fcm.trim();
        user.setFcmToken(trimmed);
        user = userRepository.save(user);
        riderRepository.findByPhone(phone).ifPresent(rider -> {
          rider.setFcmToken(trimmed);
          riderRepository.save(rider);
        });
      }

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
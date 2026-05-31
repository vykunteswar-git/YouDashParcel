package com.youdash.service.impl;

import java.time.LocalDateTime;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import com.youdash.service.sms.PhoneNumberUtil;
import com.youdash.service.sms.SmsDeliveryException;
import com.youdash.service.sms.SmsService;

@Service
public class AuthServiceImpl implements AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

  @Value("${auth.test-login.enabled:false}")
  private boolean testLoginEnabled;

  @Value("${auth.test-login.phone:}")
  private String testLoginPhone;

  /** Optional comma-separated list; merged with {@link #testLoginPhone}. */
  @Value("${auth.test-login.phones:}")
  private String testLoginPhones;

  @Value("${auth.test-login.otp:1234}")
  private String testLoginOtp;

  /** When true (no real SMS yet), include the generated OTP in the API response for in-app display. Set false once Msg91 (or similar) sends OTPs. */
  @Value("${auth.otp.expose-in-response:true}")
  private boolean exposeOtpInResponse;

  @Value("${msg91.enabled:false}")
  private boolean msg91Enabled;

  @Autowired
  private OtpRepository otpRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private RiderRepository riderRepository;

  @Autowired
  private com.youdash.util.JwtUtil jwtUtil;

  @Autowired
  private SmsService smsService;

  // SEND OTP
  @Override
  public ApiResponse<OtpResponseDTO> sendOtp(OtpRequestDTO request) {
    ApiResponse<OtpResponseDTO> response = new ApiResponse<>();
    try {
      if (request == null || request.getPhoneNumber() == null || request.getPhoneNumber().isBlank()) {
        throw new RuntimeException("phoneNumber is required");
      }

      String phone = PhoneNumberUtil.normalizeNational(request.getPhoneNumber());
      boolean isTestPhone = matchesConfiguredTestLoginPhone(phone);

      String otp = isTestPhone
          ? testLoginOtp
          : String.valueOf(new Random().nextInt(9000) + 1000);

      OtpEntity otpEntity = otpRepository.findByPhoneNumber(phone)
          .orElse(new OtpEntity());
      otpEntity.setPhoneNumber(phone);
      otpEntity.setOtp(otp);
      otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(5));
      otpRepository.save(otpEntity);

      if (isTestPhone) {
        log.info("Test-login OTP for {} (SMS skipped)", phone);
      } else if (msg91Enabled) {
        smsService.sendOtp(PhoneNumberUtil.toMsg91Mobile(phone), otp);
      } else {
        System.out.println("OTP for " + phone + " = " + otp);
      }

      String responseOtp = (exposeOtpInResponse || isTestPhone) ? otp : null;
      if (msg91Enabled && !isTestPhone && !exposeOtpInResponse) {
        responseOtp = null;
      }
      response.setData(new OtpResponseDTO(phone, responseOtp));
      response.setMessage("OTP sent successfully");
      response.setMessageKey("SUCCESS");
      response.setStatus(200);
      response.setSuccess(true);
    } catch (SmsDeliveryException e) {
      response.setMessage(e.getMessage());
      response.setMessageKey("ERROR");
      response.setStatus(400);
      response.setSuccess(false);
    } catch (RuntimeException e) {
      response.setMessage(e.getMessage() != null ? e.getMessage() : "Failed to send OTP");
      response.setMessageKey("ERROR");
      response.setStatus(400);
      response.setSuccess(false);
    } catch (Exception e) {
      log.error("sendOtp failed", e);
      response.setMessage("Failed to send OTP");
      response.setSuccess(false);
    }
    return response;
  }

  private boolean matchesConfiguredTestLoginPhone(String nationalPhone) {
    if (!testLoginEnabled || nationalPhone == null || nationalPhone.isBlank()) {
      return false;
    }
    for (String configured : configuredTestLoginPhones()) {
      if (nationalPhone.equals(configured)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isTestLoginPhone(String normalizedNationalPhone) {
    return matchesConfiguredTestLoginPhone(normalizedNationalPhone);
  }

  @Override
  public boolean isTestLoginBypass(String normalizedNationalPhone, String otp) {
    return matchesConfiguredTestLoginPhone(normalizedNationalPhone)
        && otp != null
        && testLoginOtp.equals(otp.trim());
  }

  private java.util.List<String> configuredTestLoginPhones() {
    java.util.LinkedHashSet<String> phones = new java.util.LinkedHashSet<>();
    if (testLoginPhones != null && !testLoginPhones.isBlank()) {
      for (String part : testLoginPhones.split(",")) {
        addNormalizedTestPhone(phones, part);
      }
    }
    if (testLoginPhone != null && !testLoginPhone.isBlank()) {
      addNormalizedTestPhone(phones, testLoginPhone);
    }
    return java.util.List.copyOf(phones);
  }

  private void addNormalizedTestPhone(java.util.Set<String> phones, String raw) {
    if (raw == null || raw.isBlank()) {
      return;
    }
    try {
      phones.add(PhoneNumberUtil.normalizeNational(raw.trim()));
    } catch (SmsDeliveryException e) {
      phones.add(raw.trim().replaceAll("\\D", ""));
    }
  }

  // VERIFY OTP
  @Override
  public ApiResponse<UserResponseDTO> verifyOtp(VerifyOtpRequestDTO request) {
    ApiResponse<UserResponseDTO> response = new ApiResponse<>();
    try {
      System.out.println("Verifying OTP for " + request.getPhoneNumber() + " (Entered: " + request.getOtp() + ")");

      String phone = PhoneNumberUtil.normalizeNational(request.getPhoneNumber());
      boolean isTestPhone = matchesConfiguredTestLoginPhone(phone);

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

package com.youdash.controller;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OtpRequestDTO;
import com.youdash.dto.OtpResponseDTO;
import com.youdash.dto.RiderAuthResponseDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.dto.VerifyOtpRequestDTO;
import com.youdash.entity.OtpEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.repository.OtpRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.util.JwtUtil;

@RestController
@RequestMapping({"/rider-auth", "/rider/auth"})
public class RiderAuthController {

    @Autowired
    private OtpRepository otpRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/send-otp")
    public ApiResponse<OtpResponseDTO> sendOtp(@RequestBody OtpRequestDTO request) {
        // Reuse the same persistence model as /auth/send-otp (OtpEntity).
        // For consistency, we allow OTP sending even if rider doesn't exist yet.
        ApiResponse<OtpResponseDTO> response = new ApiResponse<>();
        try {
            if (request == null || request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
                throw new RuntimeException("phoneNumber is required");
            }
            String phone = request.getPhoneNumber().trim();

            // Use the same dummy OTP behavior as AuthServiceImpl (keeps client behavior predictable).
            String otp = phone.equals("9999999999") ? "1234" : String.valueOf((int) (Math.random() * 9000) + 1000);

            OtpEntity otpEntity = otpRepository.findByPhoneNumber(phone).orElse(new OtpEntity());
            otpEntity.setPhoneNumber(phone);
            otpEntity.setOtp(otp);
            otpEntity.setExpiryTime(LocalDateTime.now().plusMinutes(5));
            otpRepository.save(otpEntity);

            response.setData(new OtpResponseDTO(phone, otp));
            response.setMessage("OTP sent successfully");
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

    @PostMapping("/verify-otp")
    public ApiResponse<RiderAuthResponseDTO> verifyOtp(@RequestBody VerifyOtpRequestDTO request) {
        ApiResponse<RiderAuthResponseDTO> response = new ApiResponse<>();
        try {
            if (request == null || request.getPhoneNumber() == null || request.getPhoneNumber().trim().isEmpty()) {
                throw new RuntimeException("phoneNumber is required");
            }
            if (request.getOtp() == null || request.getOtp().trim().isEmpty()) {
                throw new RuntimeException("otp is required");
            }
            String phone = request.getPhoneNumber().trim();

            OtpEntity otpEntity = otpRepository
                    .findTopByPhoneNumberOrderByIdDesc(phone)
                    .orElseThrow(() -> new RuntimeException("OTP not found"));

            if (otpEntity.getExpiryTime() != null && otpEntity.getExpiryTime().isBefore(LocalDateTime.now())) {
                throw new RuntimeException("OTP expired");
            }
            if (!request.getOtp().trim().equals(otpEntity.getOtp())) {
                throw new RuntimeException("Invalid OTP");
            }

            RiderEntity rider = riderRepository.findByPhone(phone)
                    .orElseThrow(() -> new RuntimeException("Rider not registered with this phone number"));

            if (Boolean.TRUE.equals(rider.getIsBlocked())) {
                throw new RuntimeException("Rider is blocked");
            }

            String fcm = request.getFcmToken();
            if (fcm != null && !fcm.trim().isEmpty()) {
                rider.setFcmToken(fcm.trim());
                riderRepository.save(rider);
            }

            String token = jwtUtil.generateToken(rider.getId(), "RIDER");

            RiderResponseDTO riderDto = new RiderResponseDTO();
            riderDto.setId(rider.getId());
            riderDto.setPublicId(rider.getPublicId());
            riderDto.setName(rider.getName());
            riderDto.setPhone(rider.getPhone());
            riderDto.setVehicleId(rider.getVehicleId());
            riderDto.setVehicleType(rider.getVehicleType());
            riderDto.setIsAvailable(rider.getIsAvailable());
            riderDto.setIsBlocked(rider.getIsBlocked());
            riderDto.setRating(rider.getRating());
            riderDto.setApprovalStatus(rider.getApprovalStatus());
            riderDto.setProfileImageUrl(rider.getProfileImageUrl());
            riderDto.setAadhaarImageUrl(rider.getAadhaarImageUrl());
            riderDto.setLicenseImageUrl(rider.getLicenseImageUrl());
            riderDto.setRcImageUrl(rider.getRcImageUrl());

            RiderAuthResponseDTO payload = new RiderAuthResponseDTO();
            payload.setToken(token);
            payload.setRider(riderDto);

            response.setData(payload);
            response.setMessage("Rider login successful");
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
}


package com.youdash.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FcmTokenRequestDTO;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.entity.RiderEntity;
import com.youdash.repository.RiderRepository;
import com.youdash.security.RiderAccessVerifier;
import com.youdash.service.RiderService;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/riders")
public class RiderController {

    @Autowired
    private RiderService riderService;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RiderAccessVerifier riderAccessVerifier;

    @PostMapping("/fcm-token")
    public ApiResponse<String> saveFcmToken(@RequestBody FcmTokenRequestDTO dto, HttpServletRequest request) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            Long riderId = riderAccessVerifier.resolveActingRiderId(request);
            if (dto == null || dto.getToken() == null || dto.getToken().trim().isEmpty()) {
                throw new RuntimeException("FCM token is required");
            }

            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found"));

            rider.setFcmToken(dto.getToken().trim());
            riderRepository.save(rider);

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

    @PostMapping
    public ApiResponse<RiderResponseDTO> createRider(@RequestBody RiderRequestDTO dto) {
        return riderService.createRider(dto);
    }

    @GetMapping
    public ApiResponse<List<RiderResponseDTO>> getAllRiders() {
        return riderService.getAllRiders();
    }

    @GetMapping("/available")
    public ApiResponse<List<RiderResponseDTO>> getAvailableRiders() {
        return riderService.getAvailableRiders();
    }

    @PutMapping("/{id}/availability")
    public ApiResponse<RiderResponseDTO> updateAvailability(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> statusMap,
            HttpServletRequest request) {
        if (!riderAccessVerifier.canAccessRider(request, id)) {
            throw new RuntimeException("Access denied");
        }
        Boolean isAvailable = statusMap.get("isAvailable");
        if (isAvailable == null) {
            throw new RuntimeException("isAvailable is required");
        }
        return riderService.updateAvailability(id, isAvailable);
    }

    @PutMapping("/{id}/location")
    public ApiResponse<RiderResponseDTO> updateLocation(
            @PathVariable Long id,
            @RequestBody Map<String, Double> locationMap,
            HttpServletRequest request) {
        if (!riderAccessVerifier.canAccessRider(request, id)) {
            throw new RuntimeException("Access denied");
        }
        Double lat = locationMap.get("lat");
        Double lng = locationMap.get("lng");
        if (lat == null || lng == null) {
            throw new RuntimeException("lat and lng are required");
        }
        return riderService.updateLocation(id, lat, lng);
    }
}

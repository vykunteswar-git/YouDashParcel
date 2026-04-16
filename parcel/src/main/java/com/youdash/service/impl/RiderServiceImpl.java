package com.youdash.service.impl;

import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.RiderService;

@Service
public class RiderServiceImpl implements RiderService {

    private static final SecureRandom RIDER_ID_RANDOM = new SecureRandom();

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Override
    public ApiResponse<RiderResponseDTO> createRider(RiderRequestDTO dto) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (dto.getName() == null || dto.getName().isEmpty()) {
                throw new RuntimeException("Rider name is required");
            }
            if (dto.getPhone() == null || dto.getPhone().isEmpty()) {
                throw new RuntimeException("Rider phone is required");
            }
            String phone = dto.getPhone().trim();
            if (phone.length() < 10) {
                throw new RuntimeException("Invalid phone number");
            }
            if (riderRepository.findByPhone(phone).isPresent()) {
                throw new RuntimeException("A rider is already registered with this phone number");
            }
            if (dto.getEmergencyPhone() == null || dto.getEmergencyPhone().trim().isEmpty()) {
                throw new RuntimeException("Emergency phone is required");
            }
            if (dto.getEmergencyPhone().trim().length() < 10) {
                throw new RuntimeException("Invalid emergency phone number");
            }
            if (dto.getProfileImageUrl() == null || dto.getProfileImageUrl().trim().isEmpty()) {
                throw new RuntimeException("Profile image is required");
            }
            if (dto.getVehicleId() == null && (dto.getVehicleType() == null || dto.getVehicleType().trim().isEmpty())) {
                throw new RuntimeException("Vehicle is required");
            }

            RiderEntity rider = new RiderEntity();
            rider.setName(dto.getName());
            rider.setPhone(phone);
            rider.setPublicId(generateUniquePublicId());

            // Prefer vehicleId (dropdown) -> resolve to vehicle name; fallback to legacy
            // vehicleType string.
            String resolvedVehicleType = null;
            if (dto.getVehicleId() != null) {
                VehicleEntity vehicle = vehicleRepository.findById(dto.getVehicleId())
                        .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + dto.getVehicleId()));
                if (Boolean.FALSE.equals(vehicle.getIsActive())) {
                    throw new RuntimeException("Selected vehicle is not active");
                }
                rider.setVehicleId(vehicle.getId());
                resolvedVehicleType = vehicle.getName();
            } else {
                resolvedVehicleType = dto.getVehicleType().trim();
            }
            rider.setVehicleType(resolvedVehicleType);
            rider.setEmergencyPhone(dto.getEmergencyPhone().trim());

            rider.setProfileImageUrl(dto.getProfileImageUrl().trim());
            rider.setAadhaarImageUrl(nzTrimToNull(dto.getAadhaarImageUrl()));
            rider.setLicenseImageUrl(nzTrimToNull(dto.getLicenseImageUrl()));

            // Location is not required at registration time; it can be updated later.
            rider.setCurrentLat(0.0);
            rider.setCurrentLng(0.0);

            // Set defaults
            rider.setIsAvailable(true);
            rider.setIsBlocked(false);
            rider.setRating(0.0);

            RiderEntity savedRider = riderRepository.save(rider);

            response.setData(mapToResponseDTO(savedRider));
            response.setMessage("Rider created successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    private static String nzTrimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> getAllRiders() {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            List<RiderEntity> riders = riderRepository.findAll();
            List<RiderResponseDTO> dtos = riders.stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());

            response.setData(dtos);
            response.setMessage("Riders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage("Failed to fetch riders: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> getAvailableRiders() {
        return listRidersEligibleForAssignment();
    }

    @Override
    public ApiResponse<RiderResponseDTO> updateAvailability(Long riderId, Boolean status) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));

            if (status == null) {
                throw new RuntimeException("Availability status is required");
            }
            rider.setIsAvailable(status);
            RiderEntity updatedRider = riderRepository.save(rider);

            response.setData(mapToResponseDTO(updatedRider));
            response.setMessage("Availability updated successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<RiderResponseDTO> updateLocation(Long riderId, Double lat, Double lng) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));

            if (lat == null || lng == null) {
                throw new RuntimeException("Location cannot be null");
            }
            rider.setCurrentLat(lat);
            rider.setCurrentLng(lng);
            RiderEntity updatedRider = riderRepository.save(rider);

            response.setData(mapToResponseDTO(updatedRider));
            response.setMessage("Location updated successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> listPendingRiders() {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            List<RiderResponseDTO> dtos = riderRepository
                    .findByApprovalStatusOrderByCreatedAtDesc(RiderApprovalStatus.PENDING).stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Pending riders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> listByApprovalStatus(String approvalStatus) {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            if (approvalStatus == null || approvalStatus.isBlank()) {
                throw new RuntimeException("status is required");
            }
            List<RiderResponseDTO> dtos = riderRepository
                    .findByApprovalStatusOrderByCreatedAtDesc(approvalStatus.trim()).stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Riders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<RiderResponseDTO> approveRider(Long id) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            RiderEntity rider = riderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + id));
            rider.setApprovalStatus(RiderApprovalStatus.APPROVED);
            riderRepository.save(rider);
            response.setData(mapToResponseDTO(rider));
            response.setMessage("Rider approved");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<RiderResponseDTO> rejectRider(Long id) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            RiderEntity rider = riderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + id));
            rider.setApprovalStatus(RiderApprovalStatus.REJECTED);
            riderRepository.save(rider);
            response.setData(mapToResponseDTO(rider));
            response.setMessage("Rider rejected");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> listRidersEligibleForAssignment() {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            List<RiderResponseDTO> dtos = riderRepository.findByIsAvailableTrue().stream()
                    .filter(this::isApprovedOrLegacy)
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Eligible riders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage("Failed to fetch eligible riders: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    private boolean isApprovedOrLegacy(RiderEntity rider) {
        String ap = rider.getApprovalStatus();
        if (ap == null || ap.isBlank()) {
            return true;
        }
        return RiderApprovalStatus.APPROVED.equalsIgnoreCase(ap);
    }

    private RiderResponseDTO mapToResponseDTO(RiderEntity rider) {
        RiderResponseDTO dto = new RiderResponseDTO();
        dto.setId(rider.getId());
        dto.setPublicId(rider.getPublicId());
        dto.setName(rider.getName());
        dto.setPhone(rider.getPhone());
        dto.setVehicleId(rider.getVehicleId());
        dto.setVehicleType(rider.getVehicleType());
        dto.setIsAvailable(rider.getIsAvailable());
        dto.setIsBlocked(rider.getIsBlocked());
        dto.setRating(rider.getRating());
        dto.setApprovalStatus(rider.getApprovalStatus());
        dto.setProfileImageUrl(rider.getProfileImageUrl());
        dto.setAadhaarImageUrl(rider.getAadhaarImageUrl());
        dto.setLicenseImageUrl(rider.getLicenseImageUrl());
        dto.setRcImageUrl(rider.getRcImageUrl());
        return dto;
    }

    private String generateUniquePublicId() {
        // Not a security boundary; primary intent is to avoid exposing sequential
        // numeric ids.
        // Keep it short and URL-safe: rd-<8 base36 chars>.
        for (int i = 0; i < 10; i++) {
            long n = Math.abs(RIDER_ID_RANDOM.nextLong());
            String suffix = Long.toString(n, 36).toLowerCase(Locale.ROOT);
            if (suffix.length() > 8) {
                suffix = suffix.substring(0, 8);
            } else if (suffix.length() < 8) {
                suffix = "0".repeat(8 - suffix.length()) + suffix;
            }
            String id = "rd-" + suffix;
            if (riderRepository.findByPublicId(id).isEmpty()) {
                return id;
            }
        }
        throw new RuntimeException("Failed to generate rider id");
    }
}

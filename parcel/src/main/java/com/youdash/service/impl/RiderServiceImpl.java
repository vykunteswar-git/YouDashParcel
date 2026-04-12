package com.youdash.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.entity.RiderEntity;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.repository.RiderRepository;
import com.youdash.service.RiderService;

@Service
public class RiderServiceImpl implements RiderService {

    @Autowired
    private RiderRepository riderRepository;

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
            if (dto.getPhone().length() < 10) {
                throw new RuntimeException("Invalid phone number");
            }

            RiderEntity rider = new RiderEntity();
            rider.setName(dto.getName());
            rider.setPhone(dto.getPhone());
            rider.setVehicleType(dto.getVehicleType());
            rider.setCurrentLat(dto.getCurrentLat() != null ? dto.getCurrentLat() : 0.0);
            rider.setCurrentLng(dto.getCurrentLng() != null ? dto.getCurrentLng() : 0.0);
            
            // Set defaults
            rider.setIsAvailable(true);
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
            List<RiderResponseDTO> dtos = riderRepository.findByApprovalStatusOrderByCreatedAtDesc(RiderApprovalStatus.PENDING).stream()
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
        dto.setName(rider.getName());
        dto.setPhone(rider.getPhone());
        dto.setVehicleType(rider.getVehicleType());
        dto.setIsAvailable(rider.getIsAvailable());
        dto.setRating(rider.getRating());
        dto.setApprovalStatus(rider.getApprovalStatus());
        return dto;
    }
}

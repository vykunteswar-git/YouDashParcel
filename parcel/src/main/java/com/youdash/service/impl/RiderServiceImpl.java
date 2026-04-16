package com.youdash.service.impl;

import java.util.List;
import java.util.Locale;
import java.security.SecureRandom;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.dto.RiderSelfUpdateDTO;
import com.youdash.dto.wallet.RiderWalletTransactionDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.wallet.RiderWalletEntity;
import com.youdash.entity.wallet.RiderWalletTransactionEntity;
import com.youdash.entity.wallet.RiderWithdrawalEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.repository.wallet.RiderWalletRepository;
import com.youdash.repository.wallet.RiderWalletTransactionRepository;
import com.youdash.repository.wallet.RiderWithdrawalRepository;
import com.youdash.service.RiderService;

@Service
public class RiderServiceImpl implements RiderService {

    private static final SecureRandom RIDER_ID_RANDOM = new SecureRandom();

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderWalletRepository riderWalletRepository;

    @Autowired
    private RiderWalletTransactionRepository riderWalletTransactionRepository;

    @Autowired
    private RiderWithdrawalRepository riderWithdrawalRepository;

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
            if (dto.getVehicleNumber() == null || dto.getVehicleNumber().trim().isEmpty()) {
                throw new RuntimeException("Vehicle number is required");
            }
            String vehicleNumber = dto.getVehicleNumber().trim().toUpperCase(Locale.ROOT);
            if (vehicleNumber.length() < 4) {
                throw new RuntimeException("Invalid vehicle number");
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
            rider.setVehicleNumber(vehicleNumber);
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

    @Override
    public ApiResponse<RiderResponseDTO> getRiderProfile(RiderEntity rider) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (rider == null || rider.getId() == null) {
                throw new RuntimeException("Rider not found");
            }
            response.setData(mapToResponseDTO(rider));
            response.setMessage("Rider profile fetched");
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
    public ApiResponse<RiderResponseDTO> patchSelfProfile(Long riderId, RiderSelfUpdateDTO dto) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            if (dto == null) {
                throw new RuntimeException("Request body is required");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));

            boolean changed = false;
            if (dto.getIsAvailable() != null) {
                rider.setIsAvailable(dto.getIsAvailable());
                changed = true;
            }
            if (dto.getCurrentLat() != null && dto.getCurrentLng() != null) {
                rider.setCurrentLat(dto.getCurrentLat());
                rider.setCurrentLng(dto.getCurrentLng());
                changed = true;
            } else if (dto.getCurrentLat() != null || dto.getCurrentLng() != null) {
                throw new RuntimeException("currentLat and currentLng must be provided together");
            }
            if (dto.getEmergencyPhone() != null) {
                String ep = dto.getEmergencyPhone().trim();
                if (ep.isEmpty()) {
                    throw new RuntimeException("Emergency phone cannot be empty");
                }
                if (ep.length() < 10) {
                    throw new RuntimeException("Invalid emergency phone number");
                }
                rider.setEmergencyPhone(ep);
                changed = true;
            }
            if (dto.getFcmToken() != null) {
                String fcm = dto.getFcmToken().trim();
                if (fcm.isEmpty()) {
                    throw new RuntimeException("FCM token cannot be empty");
                }
                rider.setFcmToken(fcm);
                changed = true;
            }
            if (!changed) {
                throw new RuntimeException("No updatable fields provided");
            }

            RiderEntity updated = riderRepository.save(rider);
            response.setData(mapToResponseDTO(updated));
            response.setMessage("Profile updated successfully");
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
        dto.setVehicleNumber(rider.getVehicleNumber());
        dto.setIsAvailable(rider.getIsAvailable());
        dto.setIsBlocked(rider.getIsBlocked());
        dto.setRating(rider.getRating());
        dto.setApprovalStatus(rider.getApprovalStatus());
        dto.setEmergencyPhone(rider.getEmergencyPhone());
        dto.setCurrentLat(rider.getCurrentLat());
        dto.setCurrentLng(rider.getCurrentLng());
        dto.setFcmToken(rider.getFcmToken());
        dto.setProfileImageUrl(rider.getProfileImageUrl());
        dto.setAadhaarImageUrl(rider.getAadhaarImageUrl());
        dto.setLicenseImageUrl(rider.getLicenseImageUrl());
        enrichWalletAndHistory(dto, rider.getId());
        return dto;
    }

    private void enrichWalletAndHistory(RiderResponseDTO dto, Long riderId) {
        if (riderId == null) {
            return;
        }
        dto.setTotalOrdersDelivered(orderRepository.countByRiderIdAndStatus(riderId, OrderStatus.DELIVERED));

        RiderWalletEntity w = riderWalletRepository.findByRiderId(riderId).orElse(null);
        if (w != null) {
            dto.setWalletCurrentBalance(round2(w.getCurrentBalance()));
            dto.setWalletTotalEarnings(round2(w.getTotalEarnings()));
            dto.setWalletTotalWithdrawn(round2(w.getTotalWithdrawn()));
            dto.setWalletCodPendingAmount(round2(w.getCodPendingAmount()));
            dto.setWalletWithdrawalPendingAmount(round2(w.getWithdrawalPendingAmount()));
            dto.setWalletNetAvailable(round2(w.getCurrentBalance() - w.getCodPendingAmount() - w.getWithdrawalPendingAmount()));
        }

        var page = PageRequest.of(0, 10);
        dto.setRecentWalletTransactions(
                riderWalletTransactionRepository.findByRiderIdOrderByCreatedAtDesc(riderId, page).stream()
                        .map(this::toWalletTxnDto)
                        .collect(Collectors.toList()));
        dto.setRecentWithdrawals(
                riderWithdrawalRepository.findByRiderIdOrderByCreatedAtDesc(riderId, page).stream()
                        .map(this::toWithdrawalDto)
                        .collect(Collectors.toList()));
        dto.setRecentOrders(
                orderRepository.findByRiderIdOrderByCreatedAtDesc(riderId, page).stream()
                        .map(this::toRiderOrderPreview)
                        .collect(Collectors.toList()));
    }

    private RiderWalletTransactionDTO toWalletTxnDto(RiderWalletTransactionEntity e) {
        RiderWalletTransactionDTO d = new RiderWalletTransactionDTO();
        d.setId(e.getId());
        d.setType(e.getType() != null ? e.getType().name() : null);
        d.setAmount(e.getAmount());
        d.setReferenceType(e.getReferenceType() != null ? e.getReferenceType().name() : null);
        d.setReferenceId(e.getReferenceId());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setNote(e.getNote());
        d.setMetadataJson(e.getMetadataJson());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return d;
    }

    private RiderWithdrawalDTO toWithdrawalDto(RiderWithdrawalEntity e) {
        RiderWithdrawalDTO d = new RiderWithdrawalDTO();
        d.setId(e.getId());
        d.setAmount(e.getAmount());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setAccountHolderName(e.getBankAccountName());
        d.setAccountNumber(e.getBankAccountNumber());
        d.setIfsc(e.getBankIfsc());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return d;
    }

    private OrderResponseDTO toRiderOrderPreview(OrderEntity o) {
        return OrderResponseDTO.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .paymentType(o.getPaymentType())
                .status(o.getStatus())
                .riderId(o.getRiderId())
                .totalAmount(o.getTotalAmount())
                .displayOrderId(o.getDisplayOrderId())
                .paymentStatus(o.getPaymentStatus())
                .codCollectedAmount(o.getCodCollectedAmount())
                .codCollectionMode(o.getCodCollectionMode())
                .codSettlementStatus(o.getCodSettlementStatus())
                .distanceKm(o.getDistanceKm())
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .build();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
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

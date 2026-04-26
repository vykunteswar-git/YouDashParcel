package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminLoginDTO;
import com.youdash.dto.AdminResponseDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.entity.AdminEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.repository.AdminRepository;
import com.youdash.repository.UserRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.AdminService;
import com.youdash.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    // --- ADMIN AUTHENTICATION ---

    @Override
    public ApiResponse<AdminResponseDTO> login(AdminLoginDTO dto) {
        ApiResponse<AdminResponseDTO> response = new ApiResponse<>();
        try {
            AdminEntity admin = adminRepository.findByEmail(dto.getEmail())
                    .orElseThrow(() -> new RuntimeException("Admin not found with email: " + dto.getEmail()));

            if (!Boolean.TRUE.equals(admin.getIsActive())) {
                throw new RuntimeException("Admin is inactive");
            }

            if (!passwordEncoder.matches(dto.getPassword(), admin.getPassword())) {
                throw new RuntimeException("Invalid password");
            }

            String token = jwtUtil.generateToken(admin.getId(), "ADMIN");

            AdminResponseDTO adminDTO = new AdminResponseDTO();
            adminDTO.setId(admin.getId());
            adminDTO.setEmail(admin.getEmail());
            adminDTO.setToken(token);

            response.setData(adminDTO);
            response.setMessage("Login successful");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    // --- VEHICLE MANAGEMENT ---

    @Override
    public ApiResponse<VehicleDTO> createVehicle(VehicleDTO dto) {
        ApiResponse<VehicleDTO> response = new ApiResponse<>();
        try {
            validateVehicle(dto);
            VehicleEntity entity = new VehicleEntity();
            entity.setName(dto.getName().trim());
            entity.setPricePerKm(dto.getPricePerKm());
            entity.setBaseFare(dto.getBaseFare());
            entity.setMinimumKm(dto.getMinimumKm() != null ? dto.getMinimumKm() : 0.0);
            entity.setMaxWeight(dto.getMaxWeight());
            if (dto.getImageUrl() != null && !dto.getImageUrl().isBlank()) {
                entity.setImageUrl(dto.getImageUrl().trim());
            }
            entity.setIsActive(true); // Default

            VehicleEntity saved = vehicleRepository.save(entity);
            response.setData(mapToVehicleDTO(saved));
            response.setMessage("Vehicle created successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<VehicleDTO>> getAllVehicles() {
        ApiResponse<List<VehicleDTO>> response = new ApiResponse<>();
        try {
            List<VehicleEntity> vehicles = vehicleRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            List<VehicleDTO> dtos = vehicles.stream().map(this::mapToVehicleDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Vehicles fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<VehicleDTO>> getActiveVehicles() {
        ApiResponse<List<VehicleDTO>> response = new ApiResponse<>();
        try {
            List<VehicleEntity> vehicles = vehicleRepository.findByIsActiveTrue();
            List<VehicleDTO> dtos = vehicles.stream().map(this::mapToVehicleDTO).collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Active vehicles fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<VehicleDTO> updateVehicle(Long id, VehicleDTO dto) {
        ApiResponse<VehicleDTO> response = new ApiResponse<>();
        try {
            VehicleEntity entity = vehicleRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + id));
            
            if (dto.getName() != null && !dto.getName().isEmpty()) entity.setName(dto.getName());
            if (dto.getPricePerKm() != null) {

                entity.setPricePerKm(dto.getPricePerKm());
                
            }
            if (dto.getBaseFare() != null) entity.setBaseFare(dto.getBaseFare());
            if (dto.getMinimumKm() != null) entity.setMinimumKm(dto.getMinimumKm());
            if (dto.getMaxWeight() != null) entity.setMaxWeight(dto.getMaxWeight());
            if (dto.getImageUrl() != null && !dto.getImageUrl().isEmpty()) entity.setImageUrl(dto.getImageUrl());
            if (dto.getIsActive() != null) entity.setIsActive(dto.getIsActive());

            VehicleEntity updated = vehicleRepository.save(Objects.requireNonNull(entity));
            response.setData(mapToVehicleDTO(updated));
                
            response.setMessage("Vehicle updated successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    // --- USER MANAGEMENT ---

    @Override
    public ApiResponse<String> hardDeleteUser(Long userId) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (userId == null) {
                throw new RuntimeException("userId is required");
            }
            if (!userRepository.existsById(userId)) {
                throw new RuntimeException("User not found with id: " + userId);
            }
            userRepository.deleteById(userId);
            response.setData("User " + userId + " permanently deleted");
            response.setMessage("User deleted");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            // FK violations (orders referencing this user) surface here — return a clear message.
            String msg = e.getMessage();
            if (msg != null && (msg.toLowerCase().contains("foreign key") || msg.toLowerCase().contains("constraint"))) {
                msg = "Cannot delete user " + userId + ": related records exist (orders, wallet, etc.). "
                        + "Soft-delete via DELETE /users/{id} instead, or remove related data first.";
            }
            response.setMessage(msg);
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    // --- HELPER METHODS (Manual Mapping & Validation) ---

    private void validateVehicle(VehicleDTO dto) {
        if (dto.getName() == null || dto.getName().trim().isEmpty())
            throw new RuntimeException("Name is required");
        if (dto.getPricePerKm() == null || dto.getPricePerKm() <= 0)
            throw new RuntimeException("Price per Km must be > 0");
        if (dto.getBaseFare() == null || dto.getBaseFare() < 0)
            throw new RuntimeException("Base fare must be >= 0");
        if (dto.getMinimumKm() != null && dto.getMinimumKm() < 0)
            throw new RuntimeException("Minimum Km must be >= 0");
        if (dto.getMaxWeight() != null && dto.getMaxWeight() <= 0)
            throw new RuntimeException("Max weight must be > 0 when provided");
    }

    private VehicleDTO mapToVehicleDTO(VehicleEntity entity) {
        VehicleDTO dto = new VehicleDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setPricePerKm(entity.getPricePerKm());
        dto.setBaseFare(entity.getBaseFare());
        dto.setMinimumKm(entity.getMinimumKm());
        dto.setMaxWeight(entity.getMaxWeight());
        dto.setImageUrl(entity.getImageUrl());
        dto.setIsActive(entity.getIsActive());
        return dto;
    }

    private void setErrorResponse(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

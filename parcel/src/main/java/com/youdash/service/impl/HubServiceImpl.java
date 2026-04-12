package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRequestDTO;
import com.youdash.dto.HubResponseDTO;
import com.youdash.entity.HubEntity;
import com.youdash.repository.HubRepository;
import com.youdash.service.HubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HubServiceImpl implements HubService {

    @Autowired
    private HubRepository hubRepository;

    @Override
    public ApiResponse<HubResponseDTO> create(HubRequestDTO dto) {
        ApiResponse<HubResponseDTO> response = new ApiResponse<>();
        try {
            validate(dto, true);
            HubEntity e = new HubEntity();
            apply(e, dto, true);
            e.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
            HubEntity saved = hubRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Hub created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<HubResponseDTO>> list() {
        ApiResponse<List<HubResponseDTO>> response = new ApiResponse<>();
        try {
            List<HubResponseDTO> list = hubRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<HubResponseDTO> update(Long id, HubRequestDTO dto) {
        ApiResponse<HubResponseDTO> response = new ApiResponse<>();
        try {
            HubEntity e = hubRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Hub not found"));
            validate(dto, false);
            apply(e, dto, false);
            if (dto.getIsActive() != null) {
                e.setIsActive(dto.getIsActive());
            }
            HubEntity saved = hubRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Hub updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private void validate(HubRequestDTO dto, boolean create) {
        if (create) {
            if (dto.getName() == null || dto.getName().isBlank()) {
                throw new RuntimeException("name is required");
            }
            if (dto.getLat() == null || dto.getLng() == null) {
                throw new RuntimeException("lat and lng are required");
            }
        }
    }

    private void apply(HubEntity e, HubRequestDTO dto, boolean create) {
        if (dto.getName() != null && !dto.getName().isBlank()) {
            e.setName(dto.getName().trim());
        }
        if (dto.getCity() != null) {
            e.setCity(dto.getCity().isBlank() ? null : dto.getCity().trim());
        }
        if (dto.getLat() != null) {
            e.setLat(dto.getLat());
        }
        if (dto.getLng() != null) {
            e.setLng(dto.getLng());
        }
        if (dto.getZoneId() != null) {
            e.setZoneId(dto.getZoneId());
        }
    }

    private HubResponseDTO toDto(HubEntity e) {
        return HubResponseDTO.builder()
                .id(e.getId())
                .name(e.getName())
                .city(e.getCity())
                .lat(e.getLat())
                .lng(e.getLng())
                .zoneId(e.getZoneId())
                .isActive(e.getIsActive())
                .build();
    }

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

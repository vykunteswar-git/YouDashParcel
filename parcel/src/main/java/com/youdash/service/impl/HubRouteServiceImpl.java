package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteRequestDTO;
import com.youdash.dto.HubRouteResponseDTO;
import com.youdash.entity.HubRouteEntity;
import com.youdash.repository.HubRepository;
import com.youdash.repository.HubRouteRepository;
import com.youdash.service.HubRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HubRouteServiceImpl implements HubRouteService {

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Autowired
    private HubRepository hubRepository;

    @Override
    public ApiResponse<HubRouteResponseDTO> create(HubRouteRequestDTO dto) {
        ApiResponse<HubRouteResponseDTO> response = new ApiResponse<>();
        try {
            validate(dto, true);
            HubRouteEntity e = new HubRouteEntity();
            e.setOriginHubId(dto.getOriginHubId());
            e.setDestinationHubId(dto.getDestinationHubId());
            e.setRatePerKm(dto.getRatePerKm());
            e.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
            HubRouteEntity saved = hubRouteRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Hub route created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<HubRouteResponseDTO>> list() {
        ApiResponse<List<HubRouteResponseDTO>> response = new ApiResponse<>();
        try {
            List<HubRouteResponseDTO> list = hubRouteRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
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
    public ApiResponse<HubRouteResponseDTO> update(Long id, HubRouteRequestDTO dto) {
        ApiResponse<HubRouteResponseDTO> response = new ApiResponse<>();
        try {
            HubRouteEntity e = hubRouteRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Hub route not found"));
            if (dto.getOriginHubId() != null) {
                hubRepository.findById(dto.getOriginHubId()).orElseThrow(() -> new RuntimeException("Origin hub not found"));
                e.setOriginHubId(dto.getOriginHubId());
            }
            if (dto.getDestinationHubId() != null) {
                hubRepository.findById(dto.getDestinationHubId()).orElseThrow(() -> new RuntimeException("Destination hub not found"));
                e.setDestinationHubId(dto.getDestinationHubId());
            }
            if (dto.getRatePerKm() != null) {
                if (dto.getRatePerKm() <= 0) {
                    throw new RuntimeException("ratePerKm must be > 0");
                }
                e.setRatePerKm(dto.getRatePerKm());
            }
            if (dto.getIsActive() != null) {
                e.setIsActive(dto.getIsActive());
            }
            HubRouteEntity saved = hubRouteRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Hub route updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private void validate(HubRouteRequestDTO dto, boolean create) {
        if (create) {
            if (dto.getOriginHubId() == null || dto.getDestinationHubId() == null) {
                throw new RuntimeException("originHubId and destinationHubId are required");
            }
            if (Objects.equals(dto.getOriginHubId(), dto.getDestinationHubId())) {
                throw new RuntimeException("origin and destination hub must differ");
            }
            if (dto.getRatePerKm() == null || dto.getRatePerKm() <= 0) {
                throw new RuntimeException("ratePerKm must be > 0");
            }
            hubRepository.findById(dto.getOriginHubId()).orElseThrow(() -> new RuntimeException("Origin hub not found"));
            hubRepository.findById(dto.getDestinationHubId()).orElseThrow(() -> new RuntimeException("Destination hub not found"));
        }
    }

    private HubRouteResponseDTO toDto(HubRouteEntity e) {
        return HubRouteResponseDTO.builder()
                .id(e.getId())
                .originHubId(e.getOriginHubId())
                .destinationHubId(e.getDestinationHubId())
                .ratePerKm(e.getRatePerKm())
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

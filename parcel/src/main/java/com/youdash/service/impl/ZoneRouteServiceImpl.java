package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRouteRequestDTO;
import com.youdash.dto.ZoneRouteResponseDTO;
import com.youdash.entity.ZoneRouteEntity;
import com.youdash.repository.ZoneRepository;
import com.youdash.repository.ZoneRouteRepository;
import com.youdash.service.ZoneRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ZoneRouteServiceImpl implements ZoneRouteService {

    @Autowired
    private ZoneRouteRepository zoneRouteRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Override
    public ApiResponse<ZoneRouteResponseDTO> create(ZoneRouteRequestDTO dto) {
        ApiResponse<ZoneRouteResponseDTO> response = new ApiResponse<>();
        try {
            validate(dto, true);
            zoneRouteRepository
                    .findByOriginZoneIdAndDestinationZoneId(
                            dto.getOriginZoneId(), dto.getDestinationZoneId())
                    .ifPresent(z -> {
                        throw new RuntimeException("A zone route already exists for this pair (edit the existing row)");
                    });
            ZoneRouteEntity e = new ZoneRouteEntity();
            e.setOriginZoneId(dto.getOriginZoneId());
            e.setDestinationZoneId(dto.getDestinationZoneId());
            e.setRatePerKm(dto.getRatePerKm());
            e.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
            ZoneRouteEntity saved = zoneRouteRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Zone route created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<ZoneRouteResponseDTO>> list() {
        ApiResponse<List<ZoneRouteResponseDTO>> response = new ApiResponse<>();
        try {
            List<ZoneRouteResponseDTO> list = zoneRouteRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
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
    public ApiResponse<ZoneRouteResponseDTO> update(Long id, ZoneRouteRequestDTO dto) {
        ApiResponse<ZoneRouteResponseDTO> response = new ApiResponse<>();
        try {
            ZoneRouteEntity e = zoneRouteRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Zone route not found"));
            if (dto.getOriginZoneId() != null) {
                zoneRepository.findById(dto.getOriginZoneId())
                        .orElseThrow(() -> new RuntimeException("Origin zone not found"));
                e.setOriginZoneId(dto.getOriginZoneId());
            }
            if (dto.getDestinationZoneId() != null) {
                zoneRepository.findById(dto.getDestinationZoneId())
                        .orElseThrow(() -> new RuntimeException("Destination zone not found"));
                e.setDestinationZoneId(dto.getDestinationZoneId());
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
            ZoneRouteEntity saved = zoneRouteRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Zone route updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private void validate(ZoneRouteRequestDTO dto, boolean create) {
        if (create) {
            if (dto.getOriginZoneId() == null || dto.getDestinationZoneId() == null) {
                throw new RuntimeException("originZoneId and destinationZoneId are required");
            }
            if (Objects.equals(dto.getOriginZoneId(), dto.getDestinationZoneId())) {
                throw new RuntimeException("origin and destination zone must differ");
            }
            if (dto.getRatePerKm() == null || dto.getRatePerKm() <= 0) {
                throw new RuntimeException("ratePerKm must be > 0");
            }
        }
        if (dto.getOriginZoneId() != null) {
            zoneRepository.findById(dto.getOriginZoneId()).orElseThrow(() -> new RuntimeException("Origin zone not found"));
        }
        if (dto.getDestinationZoneId() != null) {
            zoneRepository.findById(dto.getDestinationZoneId())
                    .orElseThrow(() -> new RuntimeException("Destination zone not found"));
        }
    }

    private ZoneRouteResponseDTO toDto(ZoneRouteEntity e) {
        return ZoneRouteResponseDTO.builder()
                .id(e.getId())
                .originZoneId(e.getOriginZoneId())
                .destinationZoneId(e.getDestinationZoneId())
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

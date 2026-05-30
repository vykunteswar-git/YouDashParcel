package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRouteSLARequestDTO;
import com.youdash.dto.ZoneRouteSLAResponseDTO;
import com.youdash.entity.ZoneRouteSlaEntity;
import com.youdash.repository.ZoneRouteRepository;
import com.youdash.repository.ZoneRouteSlaRepository;
import com.youdash.service.ZoneRouteSlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ZoneRouteSlaServiceImpl implements ZoneRouteSlaService {

    public static final String DELIVERY_NEXT_DAY = "NEXT_DAY";
    public static final String DELIVERY_HOURS = "HOURS";

    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    @Autowired
    private ZoneRouteSlaRepository zoneRouteSlaRepository;

    @Autowired
    private ZoneRouteRepository zoneRouteRepository;

    @Override
    public ApiResponse<ZoneRouteSLAResponseDTO> create(ZoneRouteSLARequestDTO dto) {
        ApiResponse<ZoneRouteSLAResponseDTO> response = new ApiResponse<>();
        try {
            validateZoneRoute(dto.getZoneRouteId(), true);
            ZoneRouteSlaEntity e = new ZoneRouteSlaEntity();
            applyDto(e, dto, true);
            ZoneRouteSlaEntity saved = zoneRouteSlaRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Zone route SLA created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<ZoneRouteSLAResponseDTO>> listByZoneRouteId(Long zoneRouteId) {
        ApiResponse<List<ZoneRouteSLAResponseDTO>> response = new ApiResponse<>();
        try {
            if (zoneRouteId == null) {
                throw new RuntimeException("zoneRouteId is required");
            }
            List<ZoneRouteSLAResponseDTO> list = zoneRouteSlaRepository
                    .findByZoneRouteIdOrderByPriorityAsc(zoneRouteId).stream()
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
    public ApiResponse<ZoneRouteSLAResponseDTO> update(Long id, ZoneRouteSLARequestDTO dto) {
        ApiResponse<ZoneRouteSLAResponseDTO> response = new ApiResponse<>();
        try {
            ZoneRouteSlaEntity e = zoneRouteSlaRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Zone route SLA not found"));
            applyDto(e, dto, false);
            ZoneRouteSlaEntity saved = zoneRouteSlaRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Zone route SLA updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private void validateZoneRoute(Long zoneRouteId, boolean create) {
        if (create && zoneRouteId == null) {
            throw new RuntimeException("zoneRouteId is required");
        }
        if (zoneRouteId != null) {
            zoneRouteRepository.findById(zoneRouteId).orElseThrow(() -> new RuntimeException("Zone route not found"));
        }
    }

    private void applyDto(ZoneRouteSlaEntity e, ZoneRouteSLARequestDTO dto, boolean create) {
        if (dto.getZoneRouteId() != null) {
            zoneRouteRepository.findById(dto.getZoneRouteId()).orElseThrow(() -> new RuntimeException("Zone route not found"));
            e.setZoneRouteId(dto.getZoneRouteId());
        } else if (create) {
            throw new RuntimeException("zoneRouteId is required");
        }
        if (dto.getCutoffTime() != null) {
            e.setCutoffTime(dto.getCutoffTime().isBlank() ? null : parseTime(dto.getCutoffTime(), "cutoffTime"));
        }
        if (dto.getDeliveryType() != null) {
            String t = normalizeType(dto.getDeliveryType());
            if (!DELIVERY_NEXT_DAY.equals(t) && !DELIVERY_HOURS.equals(t)) {
                throw new RuntimeException("deliveryType must be NEXT_DAY or HOURS");
            }
            e.setDeliveryType(t);
        }
        if (dto.getDeliveryTime() != null) {
            e.setDeliveryTime(dto.getDeliveryTime().isBlank() ? null : parseTime(dto.getDeliveryTime(), "deliveryTime"));
        }
        if (dto.getDeliveredWithinHours() != null) {
            e.setDeliveredWithinHours(dto.getDeliveredWithinHours());
        }
        if (dto.getPriority() != null) {
            e.setPriority(dto.getPriority());
        }
        if (dto.getIsActive() != null) {
            e.setIsActive(dto.getIsActive());
        }
        validateEntityRules(e);
    }

    private void validateEntityRules(ZoneRouteSlaEntity e) {
        String type = normalizeType(e.getDeliveryType());
        if (type.isEmpty()) {
            throw new RuntimeException("deliveryType is required");
        }
        if (DELIVERY_NEXT_DAY.equals(type) && e.getDeliveryTime() == null) {
            throw new RuntimeException("deliveryTime is required for NEXT_DAY");
        }
        if (DELIVERY_HOURS.equals(type)
                && (e.getDeliveredWithinHours() == null || e.getDeliveredWithinHours() <= 0)) {
            throw new RuntimeException("deliveredWithinHours is required and must be > 0 for HOURS");
        }
        if (e.getPriority() == null) {
            throw new RuntimeException("priority is required");
        }
    }

    private static String normalizeType(String raw) {
        return raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalTime parseTime(String s, String field) {
        try {
            return LocalTime.parse(s.trim(), ISO_TIME);
        } catch (DateTimeParseException ex) {
            throw new RuntimeException("Invalid " + field + ", use ISO local time e.g. 15:00");
        }
    }

    private ZoneRouteSLAResponseDTO toDto(ZoneRouteSlaEntity e) {
        return ZoneRouteSLAResponseDTO.builder()
                .id(e.getId())
                .zoneRouteId(e.getZoneRouteId())
                .cutoffTime(e.getCutoffTime() != null ? e.getCutoffTime().format(ISO_TIME) : null)
                .deliveryType(e.getDeliveryType())
                .deliveryTime(e.getDeliveryTime() != null ? e.getDeliveryTime().format(ISO_TIME) : null)
                .deliveredWithinHours(e.getDeliveredWithinHours())
                .priority(e.getPriority())
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

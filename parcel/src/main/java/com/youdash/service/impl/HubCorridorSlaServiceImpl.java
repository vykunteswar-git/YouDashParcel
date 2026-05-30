package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubCorridorSlaRequestDTO;
import com.youdash.dto.HubCorridorSlaResponseDTO;
import com.youdash.entity.HubCorridorSlaEntity;
import com.youdash.repository.HubCorridorSlaRepository;
import com.youdash.repository.HubRepository;
import com.youdash.repository.ZoneRepository;
import com.youdash.service.HubCorridorSlaService;
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
public class HubCorridorSlaServiceImpl implements HubCorridorSlaService {

    public static final String DELIVERY_NEXT_DAY = "NEXT_DAY";
    public static final String DELIVERY_HOURS = "HOURS";

    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_LOCAL_TIME;

    @Autowired
    private HubCorridorSlaRepository hubCorridorSlaRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Override
    public ApiResponse<HubCorridorSlaResponseDTO> create(HubCorridorSlaRequestDTO dto) {
        ApiResponse<HubCorridorSlaResponseDTO> response = new ApiResponse<>();
        try {
            validateRefs(dto.getHubId(), dto.getDestinationZoneId(), true);
            HubCorridorSlaEntity e = new HubCorridorSlaEntity();
            applyDto(e, dto, true);
            HubCorridorSlaEntity saved = hubCorridorSlaRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Hub corridor SLA created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<HubCorridorSlaResponseDTO>> listByHubId(Long hubId) {
        ApiResponse<List<HubCorridorSlaResponseDTO>> response = new ApiResponse<>();
        try {
            if (hubId == null) {
                throw new RuntimeException("hubId is required");
            }
            hubRepository.findById(hubId).orElseThrow(() -> new RuntimeException("Hub not found"));
            List<HubCorridorSlaResponseDTO> list = hubCorridorSlaRepository
                    .findByHubIdOrderByDestinationZoneIdAscPriorityAsc(hubId).stream()
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
    public ApiResponse<HubCorridorSlaResponseDTO> update(Long id, HubCorridorSlaRequestDTO dto) {
        ApiResponse<HubCorridorSlaResponseDTO> response = new ApiResponse<>();
        try {
            HubCorridorSlaEntity e = hubCorridorSlaRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Hub corridor SLA not found"));
            if (dto.getHubId() != null || dto.getDestinationZoneId() != null) {
                validateRefs(
                        dto.getHubId() != null ? dto.getHubId() : e.getHubId(),
                        dto.getDestinationZoneId() != null ? dto.getDestinationZoneId() : e.getDestinationZoneId(),
                        false);
            }
            applyDto(e, dto, false);
            HubCorridorSlaEntity saved = hubCorridorSlaRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Hub corridor SLA updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private void validateRefs(Long hubId, Long destinationZoneId, boolean create) {
        if (create) {
            if (hubId == null || destinationZoneId == null) {
                throw new RuntimeException("hubId and destinationZoneId are required");
            }
        }
        if (hubId != null) {
            hubRepository.findById(hubId).orElseThrow(() -> new RuntimeException("Hub not found"));
        }
        if (destinationZoneId != null) {
            zoneRepository.findById(destinationZoneId).orElseThrow(() -> new RuntimeException("Destination zone not found"));
        }
        if (hubId != null && destinationZoneId != null) {
            hubRepository.findById(hubId).ifPresent(h -> {
                if (Objects.equals(h.getZoneId(), destinationZoneId)) {
                    throw new RuntimeException("Destination zone must differ from the hub's own zone");
                }
            });
        }
    }

    private void applyDto(HubCorridorSlaEntity e, HubCorridorSlaRequestDTO dto, boolean create) {
        if (dto.getHubId() != null) {
            e.setHubId(dto.getHubId());
        } else if (create) {
            throw new RuntimeException("hubId is required");
        }
        if (dto.getDestinationZoneId() != null) {
            e.setDestinationZoneId(dto.getDestinationZoneId());
        } else if (create) {
            throw new RuntimeException("destinationZoneId is required");
        }
        if (dto.getCutoffTime() != null) {
            e.setCutoffTime(dto.getCutoffTime().isBlank() ? null : parseTime(dto.getCutoffTime()));
        }
        if (dto.getDeliveryType() != null) {
            String t = normalizeType(dto.getDeliveryType());
            if (!DELIVERY_NEXT_DAY.equals(t) && !DELIVERY_HOURS.equals(t)) {
                throw new RuntimeException("deliveryType must be NEXT_DAY or HOURS");
            }
            e.setDeliveryType(t);
        }
        if (dto.getDeliveryTime() != null) {
            e.setDeliveryTime(dto.getDeliveryTime().isBlank() ? null : parseTime(dto.getDeliveryTime()));
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

    private void validateEntityRules(HubCorridorSlaEntity e) {
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

    private static LocalTime parseTime(String s) {
        try {
            return LocalTime.parse(s.trim(), ISO_TIME);
        } catch (DateTimeParseException ex) {
            throw new RuntimeException("Invalid time, use e.g. 15:00");
        }
    }

    private HubCorridorSlaResponseDTO toDto(HubCorridorSlaEntity e) {
        return HubCorridorSlaResponseDTO.builder()
                .id(e.getId())
                .hubId(e.getHubId())
                .destinationZoneId(e.getDestinationZoneId())
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

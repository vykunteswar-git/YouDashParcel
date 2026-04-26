package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteSLARequestDTO;
import com.youdash.dto.HubRouteSLAResponseDTO;
import com.youdash.dto.HubRouteSlaPreviewResponseDTO;
import com.youdash.entity.HubRouteSlaEntity;
import com.youdash.repository.HubRouteRepository;
import com.youdash.repository.HubRouteSlaRepository;
import com.youdash.service.HubRouteSlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class HubRouteSlaServiceImpl implements HubRouteSlaService {

    public static final String DELIVERY_NEXT_DAY = "NEXT_DAY";
    public static final String DELIVERY_HOURS = "HOURS";

    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    @Autowired
    private HubRouteSlaRepository hubRouteSlaRepository;

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Override
    public ApiResponse<HubRouteSLAResponseDTO> create(HubRouteSLARequestDTO dto) {
        ApiResponse<HubRouteSLAResponseDTO> response = new ApiResponse<>();
        try {
            validateAndResolveHubRoute(dto.getHubRouteId(), true);
            HubRouteSlaEntity e = new HubRouteSlaEntity();
            applyDto(e, dto, true);
            HubRouteSlaEntity saved = hubRouteSlaRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("SLA created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<HubRouteSLAResponseDTO>> listByHubRouteId(Long hubRouteId) {
        ApiResponse<List<HubRouteSLAResponseDTO>> response = new ApiResponse<>();
        try {
            if (hubRouteId == null) {
                throw new RuntimeException("hubRouteId is required");
            }
            List<HubRouteSLAResponseDTO> list = hubRouteSlaRepository
                    .findByHubRouteIdOrderByPriorityAsc(hubRouteId).stream()
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
    public ApiResponse<HubRouteSLAResponseDTO> update(Long id, HubRouteSLARequestDTO dto) {
        ApiResponse<HubRouteSLAResponseDTO> response = new ApiResponse<>();
        try {
            HubRouteSlaEntity e = hubRouteSlaRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("SLA not found"));
            applyDto(e, dto, false);
            HubRouteSlaEntity saved = hubRouteSlaRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("SLA updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public LocalDateTime calculateDelivery(Long hubRouteId) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalTime now = LocalTime.now(zone);
        LocalDateTime nowDt = LocalDateTime.now(zone);

        List<HubRouteSlaEntity> slaList =
                hubRouteSlaRepository.findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(hubRouteId);
        if (slaList.isEmpty()) {
            throw new RuntimeException("No SLA configured for this hub route");
        }

        for (HubRouteSlaEntity sla : slaList) {
            if (!matchesCutoff(sla, now)) {
                continue;
            }
            String type = normalizeType(sla.getDeliveryType());
            if (DELIVERY_NEXT_DAY.equals(type)) {
                if (sla.getDeliveryTime() == null) {
                    throw new RuntimeException("NEXT_DAY SLA requires deliveryTime");
                }
                return today.plusDays(1).atTime(sla.getDeliveryTime());
            }
            if (DELIVERY_HOURS.equals(type)) {
                if (sla.getDeliveredWithinHours() == null || sla.getDeliveredWithinHours() <= 0) {
                    throw new RuntimeException("HOURS SLA requires deliveredWithinHours > 0");
                }
                return nowDt.plusHours(sla.getDeliveredWithinHours());
            }
            throw new RuntimeException("Unknown deliveryType: " + sla.getDeliveryType());
        }
        throw new RuntimeException("No SLA matched");
    }

    @Override
    public ApiResponse<HubRouteSlaPreviewResponseDTO> preview(Long hubRouteId) {
        ApiResponse<HubRouteSlaPreviewResponseDTO> response = new ApiResponse<>();
        try {
            if (hubRouteId == null) {
                throw new RuntimeException("hubRouteId is required");
            }
            List<HubRouteSlaEntity> list =
                    hubRouteSlaRepository.findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(hubRouteId);
            List<String> messages = buildPreviewMessages(list);
            response.setData(HubRouteSlaPreviewResponseDTO.builder().messages(messages).build());
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private static boolean matchesCutoff(HubRouteSlaEntity sla, LocalTime now) {
        if (sla.getCutoffTime() == null) {
            return true;
        }
        return now.isBefore(sla.getCutoffTime());
    }

    private List<String> buildPreviewMessages(List<HubRouteSlaEntity> list) {
        List<String> messages = new ArrayList<>();
        for (HubRouteSlaEntity sla : list) {
            String type = normalizeType(sla.getDeliveryType());
            if (DELIVERY_NEXT_DAY.equals(type) && sla.getCutoffTime() != null && sla.getDeliveryTime() != null) {
                messages.add(String.format(
                        "Reach origin hub before %s — delivered by tomorrow at %s",
                        DISPLAY_TIME.format(sla.getCutoffTime()),
                        DISPLAY_TIME.format(sla.getDeliveryTime())));
            } else if (DELIVERY_HOURS.equals(type) && sla.getCutoffTime() != null && sla.getDeliveredWithinHours() != null) {
                messages.add(String.format(
                        "Reach origin hub before %s — delivered within %d hour%s",
                        DISPLAY_TIME.format(sla.getCutoffTime()),
                        sla.getDeliveredWithinHours(),
                        sla.getDeliveredWithinHours() == 1 ? "" : "s"));
            } else if (DELIVERY_HOURS.equals(type) && sla.getCutoffTime() == null && sla.getDeliveredWithinHours() != null) {
                messages.add(String.format(
                        "After all slots — delivered within %d hour%s",
                        sla.getDeliveredWithinHours(),
                        sla.getDeliveredWithinHours() == 1 ? "" : "s"));
            } else if (DELIVERY_NEXT_DAY.equals(type) && sla.getCutoffTime() == null && sla.getDeliveryTime() != null) {
                messages.add(String.format(
                        "Next available slot — delivered by next day at %s",
                        DISPLAY_TIME.format(sla.getDeliveryTime())));
            }
        }
        if (messages.isEmpty()) {
            messages.add("No delivery promises configured for this route.");
        }
        return messages;
    }

    private void validateAndResolveHubRoute(Long hubRouteId, boolean create) {
        if (create && hubRouteId == null) {
            throw new RuntimeException("hubRouteId is required");
        }
        if (hubRouteId != null) {
            hubRouteRepository.findById(hubRouteId).orElseThrow(() -> new RuntimeException("Hub route not found"));
        }
    }

    private void applyDto(HubRouteSlaEntity e, HubRouteSLARequestDTO dto, boolean create) {
        if (dto.getHubRouteId() != null) {
            hubRouteRepository.findById(dto.getHubRouteId()).orElseThrow(() -> new RuntimeException("Hub route not found"));
            e.setHubRouteId(dto.getHubRouteId());
        } else if (create) {
            throw new RuntimeException("hubRouteId is required");
        }
        if (dto.getCutoffTime() != null) {
            if (dto.getCutoffTime().isBlank()) {
                e.setCutoffTime(null);
            } else {
                e.setCutoffTime(parseTime(dto.getCutoffTime(), "cutoffTime"));
            }
        }
        if (dto.getDeliveryType() != null) {
            String t = normalizeType(dto.getDeliveryType());
            if (!DELIVERY_NEXT_DAY.equals(t) && !DELIVERY_HOURS.equals(t)) {
                throw new RuntimeException("deliveryType must be NEXT_DAY or HOURS");
            }
            e.setDeliveryType(t);
        }
        if (dto.getDeliveryTime() != null) {
            if (dto.getDeliveryTime().isBlank()) {
                e.setDeliveryTime(null);
            } else {
                e.setDeliveryTime(parseTime(dto.getDeliveryTime(), "deliveryTime"));
            }
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

    private void validateEntityRules(HubRouteSlaEntity e) {
        String type = normalizeType(e.getDeliveryType());
        if (type.isEmpty()) {
            throw new RuntimeException("deliveryType is required");
        }
        if (DELIVERY_NEXT_DAY.equals(type)) {
            if (e.getDeliveryTime() == null) {
                throw new RuntimeException("deliveryTime is required for NEXT_DAY");
            }
        }
        if (DELIVERY_HOURS.equals(type)) {
            if (e.getDeliveredWithinHours() == null || e.getDeliveredWithinHours() <= 0) {
                throw new RuntimeException("deliveredWithinHours is required and must be > 0 for HOURS");
            }
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

    private HubRouteSLAResponseDTO toDto(HubRouteSlaEntity e) {
        return HubRouteSLAResponseDTO.builder()
                .id(e.getId())
                .hubRouteId(e.getHubRouteId())
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

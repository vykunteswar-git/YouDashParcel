package com.youdash.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRequestDTO;
import com.youdash.dto.ZoneResponseDTO;
import com.youdash.entity.ZoneEntity;
import com.youdash.model.ZoneType;
import com.youdash.repository.ZoneRepository;
import com.youdash.service.ZoneService;
import com.youdash.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ZoneServiceImpl implements ZoneService {

    private static final TypeReference<List<List<Double>>> COORD_TYPE = new TypeReference<>() {
    };

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public ApiResponse<ZoneResponseDTO> createZone(ZoneRequestDTO dto) {
        ApiResponse<ZoneResponseDTO> response = new ApiResponse<>();
        try {
            validateZoneRequest(dto, true);
            ZoneEntity entity = new ZoneEntity();
            applyRequestToEntity(entity, dto, true);
            entity.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : Boolean.TRUE);
            ZoneEntity saved = zoneRepository.save(entity);
            response.setData(toResponse(saved));
            response.setMessage("Zone created successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<ZoneResponseDTO>> listZones() {
        ApiResponse<List<ZoneResponseDTO>> response = new ApiResponse<>();
        try {
            List<ZoneEntity> all = zoneRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
            List<ZoneResponseDTO> list = all.stream().map(this::toResponse).collect(Collectors.toList());
            response.setData(list);
            response.setMessage("Zones fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<ZoneResponseDTO> updateZone(Long id, ZoneRequestDTO dto) {
        ApiResponse<ZoneResponseDTO> response = new ApiResponse<>();
        try {
            ZoneEntity entity = zoneRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Zone not found with id: " + id));
            validateUpdateRequest(entity, dto);
            validateZoneRequest(dto, false);
            applyRequestToEntity(entity, dto, false);
            if (dto.getIsActive() != null) {
                entity.setIsActive(dto.getIsActive());
            }
            ZoneEntity saved = zoneRepository.save(entity);
            response.setData(toResponse(saved));
            response.setMessage("Zone updated successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setErrorResponse(response, e.getMessage());
        }
        return response;
    }

    @Override
    public Optional<ZoneEntity> findZoneContaining(double lat, double lng) {
        List<ZoneEntity> active = zoneRepository.findByIsActiveTrueOrderByIdAsc();
        for (ZoneEntity z : active) {
            if (containsPoint(z, lat, lng)) {
                return Optional.of(z);
            }
        }
        return Optional.empty();
    }

    private boolean containsPoint(ZoneEntity zone, double lat, double lng) {
        if (zone.getZoneType() == ZoneType.CIRCLE) {
            if (zone.getCenterLat() == null || zone.getCenterLng() == null || zone.getRadiusKm() == null) {
                return false;
            }
            return GeoUtils.isInsideCircle(lat, lng, zone.getCenterLat(), zone.getCenterLng(), zone.getRadiusKm());
        }
        if (zone.getZoneType() == ZoneType.POLYGON) {
            List<double[]> ring = parseRing(zone.getCoordinatesJson());
            return GeoUtils.isInsidePolygon(lat, lng, ring);
        }
        return false;
    }

    private List<double[]> parseRing(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<List<Double>> coords = objectMapper.readValue(json.trim(), COORD_TYPE);
            List<double[]> ring = new ArrayList<>();
            for (List<Double> p : coords) {
                if (p == null || p.size() < 2 || p.get(0) == null || p.get(1) == null) {
                    throw new RuntimeException("Invalid coordinate pair in polygon");
                }
                ring.add(new double[]{p.get(0), p.get(1)});
            }
            return ring;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Invalid coordinates JSON: " + e.getMessage());
        }
    }

    private void validateUpdateRequest(ZoneEntity entity, ZoneRequestDTO dto) {
        if (dto.getCoordinates() != null && dto.getZoneType() == null && entity.getZoneType() != ZoneType.POLYGON) {
            throw new RuntimeException("Set zoneType to POLYGON when updating coordinates");
        }
        if (dto.getZoneType() == ZoneType.POLYGON && dto.getCoordinates() == null
                && (entity.getCoordinatesJson() == null || entity.getCoordinatesJson().isBlank())) {
            throw new RuntimeException("POLYGON requires coordinates");
        }
    }

    private void validateZoneRequest(ZoneRequestDTO dto, boolean create) {
        if (create) {
            if (dto.getName() == null || dto.getName().isBlank()) {
                throw new RuntimeException("Name is required");
            }
            if (dto.getZoneType() == null) {
                throw new RuntimeException("zoneType is required");
            }
        }
        if (dto.getCoordinates() != null) {
            validatePolygonCoordinates(dto.getCoordinates());
        }
        ZoneType type = dto.getZoneType();
        if (type == null && !create) {
            if (dto.getRadiusKm() != null && dto.getRadiusKm() <= 0) {
                throw new RuntimeException("radiusKm must be > 0");
            }
            return;
        }
        if (type != null) {
            if (type == ZoneType.CIRCLE) {
                if (dto.getCoordinates() != null) {
                    throw new RuntimeException("CIRCLE zones must not include coordinates");
                }
                if (create || dto.getZoneType() != null) {
                    if (dto.getCenterLat() == null || dto.getCenterLng() == null || dto.getRadiusKm() == null) {
                        throw new RuntimeException("CIRCLE requires centerLat, centerLng, and radiusKm");
                    }
                    if (dto.getRadiusKm() <= 0) {
                        throw new RuntimeException("radiusKm must be > 0");
                    }
                }
            } else if (type == ZoneType.POLYGON) {
                if (dto.getCoordinates() == null) {
                    throw new RuntimeException("POLYGON requires coordinates");
                }
            }
        } else if (create) {
            throw new RuntimeException("zoneType is required");
        }
    }

    private void validatePolygonCoordinates(List<List<Double>> coordinates) {
        if (coordinates == null || coordinates.size() < 3) {
            throw new RuntimeException("POLYGON requires at least 3 points [lat, lng]");
        }
        for (List<Double> p : coordinates) {
            if (p == null || p.size() < 2 || p.get(0) == null || p.get(1) == null) {
                throw new RuntimeException("Each polygon point must be [lat, lng]");
            }
        }
    }

    private void applyRequestToEntity(ZoneEntity entity, ZoneRequestDTO dto, boolean create) {
        if (dto.getName() != null && !dto.getName().isBlank()) {
            entity.setName(dto.getName().trim());
        }
        if (dto.getCity() != null) {
            entity.setCity(dto.getCity().isBlank() ? null : dto.getCity().trim());
        }
        if (dto.getZoneType() != null) {
            entity.setZoneType(dto.getZoneType());
        }
        ZoneType effective = entity.getZoneType();
        if (effective == ZoneType.CIRCLE) {
            if (dto.getCenterLat() != null) {
                entity.setCenterLat(dto.getCenterLat());
            }
            if (dto.getCenterLng() != null) {
                entity.setCenterLng(dto.getCenterLng());
            }
            if (dto.getRadiusKm() != null) {
                entity.setRadiusKm(dto.getRadiusKm());
            }
            entity.setCoordinatesJson(null);
        } else if (effective == ZoneType.POLYGON) {
            if (dto.getCoordinates() != null) {
                try {
                    entity.setCoordinatesJson(objectMapper.writeValueAsString(dto.getCoordinates()));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to serialize coordinates: " + e.getMessage());
                }
            }
            if (create || dto.getZoneType() == ZoneType.POLYGON) {
                entity.setCenterLat(null);
                entity.setCenterLng(null);
                entity.setRadiusKm(null);
            }
        }
    }

    private ZoneResponseDTO toResponse(ZoneEntity e) {
        ZoneResponseDTO d = new ZoneResponseDTO();
        d.setId(e.getId());
        d.setName(e.getName());
        d.setCity(e.getCity());
        d.setIsActive(e.getIsActive());
        d.setZoneType(e.getZoneType());
        d.setCenterLat(e.getCenterLat());
        d.setCenterLng(e.getCenterLng());
        d.setRadiusKm(e.getRadiusKm());
        if (e.getCoordinatesJson() != null && !e.getCoordinatesJson().isBlank()) {
            try {
                d.setCoordinates(objectMapper.readValue(e.getCoordinatesJson(), COORD_TYPE));
            } catch (Exception ignored) {
                d.setCoordinates(List.of());
            }
        }
        return d;
    }

    private void setErrorResponse(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

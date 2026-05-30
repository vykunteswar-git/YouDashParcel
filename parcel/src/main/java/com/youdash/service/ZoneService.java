package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRequestDTO;
import com.youdash.dto.ZoneResponseDTO;
import com.youdash.entity.ZoneEntity;

import java.util.List;
import java.util.Optional;

public interface ZoneService {

    ApiResponse<ZoneResponseDTO> createZone(ZoneRequestDTO dto);

    ApiResponse<List<ZoneResponseDTO>> listZones();

    ApiResponse<ZoneResponseDTO> updateZone(Long id, ZoneRequestDTO dto);

    /**
     * First active zone that contains the point (stable order: id ascending).
     * Overlapping zones are ambiguous; avoid overlaps in admin data.
     */
    Optional<ZoneEntity> findZoneContaining(double lat, double lng);

    /**
     * First inactive zone polygon/circle that contains the point (id ascending).
     */
    Optional<ZoneEntity> findInactiveZoneContaining(double lat, double lng);

    /**
     * When pickup and drop lie in the same paused (inactive) zone, returns a user-facing block message.
     * Cross-city (different inactive zones or mixed with active) is not blocked here.
     */
    Optional<String> inactiveZoneBlockMessage(
            double pickupLat, double pickupLng, double dropLat, double dropLng);

    /**
     * Zone id for hub matching at a point: active zone if present, otherwise inactive zone polygon.
     */
    Optional<Long> resolveServingZoneIdAt(double lat, double lng);
}

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
}

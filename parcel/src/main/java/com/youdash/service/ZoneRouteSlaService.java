package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRouteSLARequestDTO;
import com.youdash.dto.ZoneRouteSLAResponseDTO;

import java.util.List;

public interface ZoneRouteSlaService {

    ApiResponse<ZoneRouteSLAResponseDTO> create(ZoneRouteSLARequestDTO dto);

    ApiResponse<List<ZoneRouteSLAResponseDTO>> listByZoneRouteId(Long zoneRouteId);

    ApiResponse<ZoneRouteSLAResponseDTO> update(Long id, ZoneRouteSLARequestDTO dto);
}

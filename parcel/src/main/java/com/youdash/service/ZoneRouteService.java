package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRouteRequestDTO;
import com.youdash.dto.ZoneRouteResponseDTO;

import java.util.List;

public interface ZoneRouteService {

    ApiResponse<ZoneRouteResponseDTO> create(ZoneRouteRequestDTO dto);

    ApiResponse<List<ZoneRouteResponseDTO>> list();

    ApiResponse<ZoneRouteResponseDTO> update(Long id, ZoneRouteRequestDTO dto);
}

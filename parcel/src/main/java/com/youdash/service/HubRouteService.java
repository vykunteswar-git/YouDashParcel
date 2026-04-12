package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteRequestDTO;
import com.youdash.dto.HubRouteResponseDTO;

import java.util.List;

public interface HubRouteService {

    ApiResponse<HubRouteResponseDTO> create(HubRouteRequestDTO dto);

    ApiResponse<List<HubRouteResponseDTO>> list();

    ApiResponse<HubRouteResponseDTO> update(Long id, HubRouteRequestDTO dto);
}

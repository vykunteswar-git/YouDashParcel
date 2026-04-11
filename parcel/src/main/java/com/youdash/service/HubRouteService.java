package com.youdash.service;

import com.youdash.dto.HubRouteRequestDTO;
import com.youdash.dto.HubRouteResponseDTO;

import java.util.List;

public interface HubRouteService {

    HubRouteResponseDTO create(HubRouteRequestDTO dto);

    List<HubRouteResponseDTO> listAll();

    HubRouteResponseDTO update(Long id, HubRouteRequestDTO dto);

    void softDelete(Long id);
}

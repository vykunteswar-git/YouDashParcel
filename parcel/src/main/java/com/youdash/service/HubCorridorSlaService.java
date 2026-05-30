package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubCorridorSlaRequestDTO;
import com.youdash.dto.HubCorridorSlaResponseDTO;

import java.util.List;

public interface HubCorridorSlaService {

    ApiResponse<HubCorridorSlaResponseDTO> create(HubCorridorSlaRequestDTO dto);

    ApiResponse<List<HubCorridorSlaResponseDTO>> listByHubId(Long hubId);

    ApiResponse<HubCorridorSlaResponseDTO> update(Long id, HubCorridorSlaRequestDTO dto);
}

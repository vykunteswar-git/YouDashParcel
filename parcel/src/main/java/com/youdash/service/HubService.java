package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRequestDTO;
import com.youdash.dto.HubResponseDTO;

import java.util.List;

public interface HubService {

    ApiResponse<HubResponseDTO> create(HubRequestDTO dto);

    ApiResponse<List<HubResponseDTO>> list();

    ApiResponse<HubResponseDTO> update(Long id, HubRequestDTO dto);
}

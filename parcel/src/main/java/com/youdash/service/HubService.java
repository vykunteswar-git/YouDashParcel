package com.youdash.service;

import com.youdash.dto.HubRequestDTO;
import com.youdash.dto.HubResponseDTO;
import com.youdash.dto.HubStatusPatchDTO;

import java.util.List;

public interface HubService {

    HubResponseDTO create(HubRequestDTO dto);

    List<HubResponseDTO> listAll(String cityFilter);

    HubResponseDTO getById(Long id);

    HubResponseDTO update(Long id, HubRequestDTO dto);

    void softDelete(Long id);

    HubResponseDTO updateStatus(Long id, HubStatusPatchDTO dto);
}

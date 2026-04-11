package com.youdash.service;

import com.youdash.dto.ZoneRequestDTO;
import com.youdash.dto.ZoneResponseDTO;

import java.util.List;

public interface ZoneService {

    ZoneResponseDTO create(ZoneRequestDTO dto);

    List<ZoneResponseDTO> listAll(String cityFilter);

    ZoneResponseDTO getById(Long id);

    ZoneResponseDTO update(Long id, ZoneRequestDTO dto);

    void delete(Long id);
}

package com.youdash.service;

import com.youdash.dto.ZonePricingRequestDTO;
import com.youdash.dto.ZonePricingResponseDTO;

import java.util.List;

public interface ZonePricingService {

    ZonePricingResponseDTO create(ZonePricingRequestDTO dto);

    List<ZonePricingResponseDTO> listAll();

    ZonePricingResponseDTO update(Long id, ZonePricingRequestDTO dto);

    void softDelete(Long id);
}

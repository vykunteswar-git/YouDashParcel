package com.youdash.service;

import com.youdash.dto.DeliveryOptionAdminResponseDTO;
import com.youdash.dto.DeliveryOptionRequestDTO;
import com.youdash.dto.DeliveryOptionsResponseDTO;

import java.util.List;

public interface DeliveryOptionService {

    DeliveryOptionsResponseDTO getPublicOptions();

    List<DeliveryOptionAdminResponseDTO> listAll();

    DeliveryOptionAdminResponseDTO create(DeliveryOptionRequestDTO dto);

    DeliveryOptionAdminResponseDTO update(Long id, DeliveryOptionRequestDTO dto);

    void softDelete(Long id);
}

package com.youdash.service.impl;

import com.youdash.dto.ZoneRequestDTO;
import com.youdash.dto.ZoneResponseDTO;
import com.youdash.entity.ZoneEntity;
import com.youdash.exception.ResourceNotFoundException;
import com.youdash.repository.ZonePricingRepository;
import com.youdash.repository.ZoneRepository;
import com.youdash.service.ZoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZoneServiceImpl implements ZoneService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("city"), Sort.Order.asc("name"));

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private ZonePricingRepository zonePricingRepository;

    @Override
    @Transactional
    public ZoneResponseDTO create(ZoneRequestDTO dto) {
        ZoneEntity entity = new ZoneEntity();
        applyRequest(entity, dto, true);
        ZoneEntity saved = zoneRepository.save(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponseDTO> listAll(String cityFilter) {
        List<ZoneEntity> list;
        if (cityFilter != null && !cityFilter.isBlank()) {
            list = zoneRepository.findByCityIgnoreCase(cityFilter.trim(), DEFAULT_SORT);
        } else {
            list = zoneRepository.findAll(DEFAULT_SORT);
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneResponseDTO getById(Long id) {
        ZoneEntity entity = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    @Transactional
    public ZoneResponseDTO update(Long id, ZoneRequestDTO dto) {
        ZoneEntity entity = zoneRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone not found with id: " + id));
        applyRequest(entity, dto, false);
        return toResponse(zoneRepository.save(entity));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        if (!zoneRepository.existsById(id)) {
            throw new ResourceNotFoundException("Zone not found with id: " + id);
        }
        zonePricingRepository.deleteByZone_Id(id);
        zoneRepository.deleteById(id);
    }

    private void applyRequest(ZoneEntity entity, ZoneRequestDTO dto, boolean isCreate) {
        entity.setCity(dto.getCity().trim());
        entity.setName(dto.getName().trim());
        entity.setCenterLat(dto.getCenterLat());
        entity.setCenterLng(dto.getCenterLng());
        entity.setRadiusKm(dto.getRadiusKm());
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        } else if (isCreate) {
            entity.setIsActive(true);
        }
    }

    private ZoneResponseDTO toResponse(ZoneEntity e) {
        ZoneResponseDTO dto = new ZoneResponseDTO();
        dto.setId(e.getId());
        dto.setCity(e.getCity());
        dto.setName(e.getName());
        dto.setCenterLat(e.getCenterLat());
        dto.setCenterLng(e.getCenterLng());
        dto.setRadiusKm(e.getRadiusKm());
        dto.setIsActive(e.getIsActive());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }
}

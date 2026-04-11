package com.youdash.service.impl;

import com.youdash.dto.HubRequestDTO;
import com.youdash.dto.HubResponseDTO;
import com.youdash.dto.HubStatusPatchDTO;
import com.youdash.entity.HubEntity;
import com.youdash.exception.ResourceNotFoundException;
import com.youdash.repository.HubRepository;
import com.youdash.service.HubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HubServiceImpl implements HubService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.asc("city"), Sort.Order.asc("name"));

    @Autowired
    private HubRepository hubRepository;

    @Override
    @Transactional
    public HubResponseDTO create(HubRequestDTO dto) {
        HubEntity entity = new HubEntity();
        applyRequest(entity, dto, true);
        HubEntity saved = hubRepository.save(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HubResponseDTO> listAll(String cityFilter) {
        List<HubEntity> list;
        if (cityFilter != null && !cityFilter.isBlank()) {
            list = hubRepository.findByCityIgnoreCase(cityFilter.trim(), DEFAULT_SORT);
        } else {
            list = hubRepository.findAll(DEFAULT_SORT);
        }
        return list.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public HubResponseDTO getById(Long id) {
        HubEntity entity = hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + id));
        return toResponse(entity);
    }

    @Override
    @Transactional
    public HubResponseDTO update(Long id, HubRequestDTO dto) {
        HubEntity entity = hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + id));
        applyRequest(entity, dto, false);
        return toResponse(hubRepository.save(entity));
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        HubEntity entity = hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + id));
        entity.setIsActive(false);
        hubRepository.save(entity);
    }

    @Override
    @Transactional
    public HubResponseDTO updateStatus(Long id, HubStatusPatchDTO dto) {
        HubEntity entity = hubRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Hub not found with id: " + id));
        entity.setIsActive(dto.getIsActive());
        return toResponse(hubRepository.save(entity));
    }

    private void applyRequest(HubEntity entity, HubRequestDTO dto, boolean isCreate) {
        entity.setCity(dto.getCity().trim());
        entity.setName(dto.getName().trim());
        entity.setLat(dto.getLat());
        entity.setLng(dto.getLng());
        if (dto.getIsActive() != null) {
            entity.setIsActive(dto.getIsActive());
        } else if (isCreate) {
            entity.setIsActive(true);
        }
    }

    private HubResponseDTO toResponse(HubEntity e) {
        HubResponseDTO dto = new HubResponseDTO();
        dto.setId(e.getId());
        dto.setCity(e.getCity());
        dto.setName(e.getName());
        dto.setLat(e.getLat());
        dto.setLng(e.getLng());
        dto.setIsActive(e.getIsActive());
        dto.setCreatedAt(e.getCreatedAt());
        dto.setUpdatedAt(e.getUpdatedAt());
        return dto;
    }
}

package com.youdash.service.impl;

import com.youdash.dto.DeliveryOptionAdminResponseDTO;
import com.youdash.dto.DeliveryOptionRequestDTO;
import com.youdash.dto.DeliveryOptionsResponseDTO;
import com.youdash.entity.DeliveryOptionEntity;
import com.youdash.model.DeliveryOptionCategory;
import com.youdash.repository.DeliveryOptionRepository;
import com.youdash.service.DeliveryOptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DeliveryOptionServiceImpl implements DeliveryOptionService {

    @Autowired
    private DeliveryOptionRepository repository;

    @Override
    @Transactional(readOnly = true)
    public DeliveryOptionsResponseDTO getPublicOptions() {
        DeliveryOptionsResponseDTO out = new DeliveryOptionsResponseDTO();
        out.setIncityOptions(codesFor(DeliveryOptionCategory.INCITY));
        out.setOutstationOptions(codesFor(DeliveryOptionCategory.OUTSTATION));
        return out;
    }

    private List<String> codesFor(DeliveryOptionCategory category) {
        return repository.findByCategoryAndIsActiveTrueOrderBySortOrderAsc(category).stream()
                .map(DeliveryOptionEntity::getCode)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryOptionAdminResponseDTO> listAll() {
        return repository.findAllByOrderByCategoryAscSortOrderAsc().stream()
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public DeliveryOptionAdminResponseDTO create(DeliveryOptionRequestDTO dto) {
        String code = normalizeCode(dto.getCode());
        if (repository.existsByCategoryAndCode(dto.getCategory(), code)) {
            throw new RuntimeException("Delivery option already exists for category: " + dto.getCategory() + " code: " + code);
        }
        DeliveryOptionEntity e = new DeliveryOptionEntity();
        e.setCategory(dto.getCategory());
        e.setCode(code);
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        e.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);
        return toAdminDto(repository.save(e));
    }

    @Override
    @Transactional
    public DeliveryOptionAdminResponseDTO update(Long id, DeliveryOptionRequestDTO dto) {
        DeliveryOptionEntity e = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery option not found: " + id));
        String code = normalizeCode(dto.getCode());
        if (repository.existsByCategoryAndCodeAndIdNot(dto.getCategory(), code, id)) {
            throw new RuntimeException("Another row already uses category " + dto.getCategory() + " and code " + code);
        }
        e.setCategory(dto.getCategory());
        e.setCode(code);
        e.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        if (dto.getIsActive() != null) {
            e.setIsActive(dto.getIsActive());
        }
        return toAdminDto(repository.save(e));
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        DeliveryOptionEntity e = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Delivery option not found: " + id));
        e.setIsActive(false);
        repository.save(e);
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new RuntimeException("code is required");
        }
        return code.trim().toUpperCase();
    }

    private DeliveryOptionAdminResponseDTO toAdminDto(DeliveryOptionEntity e) {
        DeliveryOptionAdminResponseDTO d = new DeliveryOptionAdminResponseDTO();
        d.setId(e.getId());
        d.setCategory(e.getCategory());
        d.setCode(e.getCode());
        d.setSortOrder(e.getSortOrder());
        d.setIsActive(e.getIsActive());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }
}

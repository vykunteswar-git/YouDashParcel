package com.youdash.service.impl;

import com.youdash.dto.WeightPricingConfigDTO;
import com.youdash.entity.WeightPricingConfigEntity;
import com.youdash.exception.BadRequestException;
import com.youdash.exception.ResourceNotFoundException;
import com.youdash.repository.WeightPricingConfigRepository;
import com.youdash.service.WeightPricingConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeightPricingConfigServiceImpl implements WeightPricingConfigService {

    private static final String TYPE_PER_KG = "PER_KG";

    @Autowired
    private WeightPricingConfigRepository repository;

    @Override
    @Transactional(readOnly = true)
    public WeightPricingConfigDTO getActive() {
        return repository.findFirstByActiveTrueOrderByIdDesc()
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Weight pricing config is not configured"));
    }

    @Override
    @Transactional
    public WeightPricingConfigDTO create(WeightPricingConfigDTO dto) {
        normalizeAndValidateType(dto.getType());
        repository.findFirstByActiveTrueOrderByIdDesc().ifPresent(e -> {
            e.setActive(false);
            repository.save(e);
        });
        WeightPricingConfigEntity entity = new WeightPricingConfigEntity();
        entity.setPricingType(TYPE_PER_KG);
        entity.setRate(dto.getRate());
        entity.setActive(true);
        return toDto(repository.save(entity));
    }

    @Override
    @Transactional
    public WeightPricingConfigDTO update(WeightPricingConfigDTO dto) {
        normalizeAndValidateType(dto.getType());
        WeightPricingConfigEntity entity = repository.findFirstByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Weight pricing config is not configured"));
        entity.setPricingType(TYPE_PER_KG);
        entity.setRate(dto.getRate());
        return toDto(repository.save(entity));
    }

    private void normalizeAndValidateType(String type) {
        if (type == null || type.isBlank()) {
            throw new BadRequestException("type is required");
        }
        if (!TYPE_PER_KG.equalsIgnoreCase(type.trim())) {
            throw new BadRequestException("Only PER_KG weight pricing type is supported");
        }
    }

    private WeightPricingConfigDTO toDto(WeightPricingConfigEntity e) {
        WeightPricingConfigDTO d = new WeightPricingConfigDTO();
        d.setId(e.getId());
        d.setType(e.getPricingType());
        d.setRate(e.getRate());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }
}

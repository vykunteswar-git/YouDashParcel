package com.youdash.service.impl;

import com.youdash.dto.GlobalDeliveryConfigDTO;
import com.youdash.entity.GlobalDeliveryConfigEntity;
import com.youdash.exception.ResourceNotFoundException;
import com.youdash.repository.GlobalDeliveryConfigRepository;
import com.youdash.service.GlobalDeliveryConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalDeliveryConfigServiceImpl implements GlobalDeliveryConfigService {

    @Autowired
    private GlobalDeliveryConfigRepository repository;

    @Override
    @Transactional(readOnly = true)
    public GlobalDeliveryConfigDTO getActive() {
        return repository.findFirstByActiveTrueOrderByIdDesc()
                .map(this::toDto)
                .orElseThrow(() -> new ResourceNotFoundException("Global delivery config is not configured"));
    }

    @Override
    @Transactional
    public GlobalDeliveryConfigDTO create(GlobalDeliveryConfigDTO dto) {
        repository.findFirstByActiveTrueOrderByIdDesc().ifPresent(e -> {
            e.setActive(false);
            repository.save(e);
        });
        GlobalDeliveryConfigEntity entity = new GlobalDeliveryConfigEntity();
        apply(entity, dto);
        entity.setActive(true);
        return toDto(repository.save(entity));
    }

    @Override
    @Transactional
    public GlobalDeliveryConfigDTO update(GlobalDeliveryConfigDTO dto) {
        GlobalDeliveryConfigEntity entity = repository.findFirstByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new ResourceNotFoundException("Global delivery config is not configured"));
        apply(entity, dto);
        return toDto(repository.save(entity));
    }

    private void apply(GlobalDeliveryConfigEntity e, GlobalDeliveryConfigDTO d) {
        e.setIncityExtensionKm(d.getIncityExtensionKm());
        e.setIncityExtraRatePerKm(d.getIncityExtraRatePerKm());
        e.setBaseFare(d.getBaseFare());
        e.setMinimumCharge(d.getMinimumCharge());
        e.setGstPercent(d.getGstPercent() != null ? d.getGstPercent() : 0.0);
        e.setPlatformFee(d.getPlatformFee() != null ? d.getPlatformFee() : 0.0);
        e.setFirstMileRatePerKm(d.getFirstMileRatePerKm() != null ? d.getFirstMileRatePerKm() : d.getIncityExtraRatePerKm());
        e.setLastMileRatePerKm(d.getLastMileRatePerKm() != null ? d.getLastMileRatePerKm() : d.getIncityExtraRatePerKm());
    }

    private GlobalDeliveryConfigDTO toDto(GlobalDeliveryConfigEntity e) {
        GlobalDeliveryConfigDTO d = new GlobalDeliveryConfigDTO();
        d.setId(e.getId());
        d.setIncityExtensionKm(e.getIncityExtensionKm());
        d.setIncityExtraRatePerKm(e.getIncityExtraRatePerKm());
        d.setBaseFare(e.getBaseFare());
        d.setMinimumCharge(e.getMinimumCharge());
        d.setGstPercent(e.getGstPercent() != null ? e.getGstPercent() : 0.0);
        d.setPlatformFee(e.getPlatformFee() != null ? e.getPlatformFee() : 0.0);
        d.setFirstMileRatePerKm(e.getFirstMileRatePerKm() != null ? e.getFirstMileRatePerKm() : e.getIncityExtraRatePerKm());
        d.setLastMileRatePerKm(e.getLastMileRatePerKm() != null ? e.getLastMileRatePerKm() : e.getIncityExtraRatePerKm());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }
}

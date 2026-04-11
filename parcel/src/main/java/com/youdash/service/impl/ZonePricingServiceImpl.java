package com.youdash.service.impl;

import com.youdash.dto.ZonePricingRequestDTO;
import com.youdash.dto.ZonePricingResponseDTO;
import com.youdash.entity.ZoneEntity;
import com.youdash.entity.ZonePricingEntity;
import com.youdash.exception.BadRequestException;
import com.youdash.exception.ResourceNotFoundException;
import com.youdash.repository.ZonePricingRepository;
import com.youdash.repository.ZoneRepository;
import com.youdash.service.ZonePricingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ZonePricingServiceImpl implements ZonePricingService {

    @Autowired
    private ZonePricingRepository zonePricingRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Override
    @Transactional
    public ZonePricingResponseDTO create(ZonePricingRequestDTO dto) {
        ZoneEntity zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new BadRequestException("zone_id does not exist: " + dto.getZoneId()));
        zonePricingRepository.findByZone_IdAndIsActiveTrue(dto.getZoneId()).ifPresent(p -> {
            throw new BadRequestException("Active zone pricing already exists for zone_id: " + dto.getZoneId());
        });
        ZonePricingEntity entity = new ZonePricingEntity();
        entity.setZone(zone);
        entity.setPickupRatePerKm(dto.getPickupRatePerKm());
        entity.setDeliveryRatePerKm(dto.getDeliveryRatePerKm());
        entity.setBaseFare(dto.getBaseFare());
        entity.setIsActive(true);
        return toResponse(zonePricingRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZonePricingResponseDTO> listAll() {
        return zonePricingRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ZonePricingResponseDTO update(Long id, ZonePricingRequestDTO dto) {
        ZonePricingEntity entity = zonePricingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone pricing not found with id: " + id));
        ZoneEntity zone = zoneRepository.findById(dto.getZoneId())
                .orElseThrow(() -> new BadRequestException("zone_id does not exist: " + dto.getZoneId()));
        zonePricingRepository.findByZone_IdAndIsActiveTrue(dto.getZoneId()).ifPresent(other -> {
            if (!other.getId().equals(id)) {
                throw new BadRequestException("Active zone pricing already exists for zone_id: " + dto.getZoneId());
            }
        });
        entity.setZone(zone);
        entity.setPickupRatePerKm(dto.getPickupRatePerKm());
        entity.setDeliveryRatePerKm(dto.getDeliveryRatePerKm());
        entity.setBaseFare(dto.getBaseFare());
        return toResponse(zonePricingRepository.save(entity));
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        ZonePricingEntity entity = zonePricingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Zone pricing not found with id: " + id));
        entity.setIsActive(false);
        zonePricingRepository.save(entity);
    }

    private ZonePricingResponseDTO toResponse(ZonePricingEntity e) {
        ZonePricingResponseDTO d = new ZonePricingResponseDTO();
        d.setId(e.getId());
        d.setZoneId(e.getZone() == null ? null : e.getZone().getId());
        d.setPickupRatePerKm(e.getPickupRatePerKm());
        d.setDeliveryRatePerKm(e.getDeliveryRatePerKm());
        d.setBaseFare(e.getBaseFare());
        d.setIsActive(e.getIsActive());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }
}

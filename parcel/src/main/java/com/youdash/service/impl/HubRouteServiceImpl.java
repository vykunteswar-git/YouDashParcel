package com.youdash.service.impl;

import com.youdash.dto.HubRouteRequestDTO;
import com.youdash.dto.HubRouteResponseDTO;
import com.youdash.entity.HubEntity;
import com.youdash.entity.HubRouteEntity;
import com.youdash.exception.BadRequestException;
import com.youdash.exception.ResourceNotFoundException;
import com.youdash.repository.HubRepository;
import com.youdash.repository.HubRouteRepository;
import com.youdash.service.HubRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class HubRouteServiceImpl implements HubRouteService {

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Autowired
    private HubRepository hubRepository;

    @Override
    @Transactional
    public HubRouteResponseDTO create(HubRouteRequestDTO dto) {
        validateRoutePayload(dto);
        if (dto.getSourceHubId().equals(dto.getDestinationHubId())) {
            throw new BadRequestException("source_hub_id and destination_hub_id must differ");
        }
        HubEntity source = requireHub(dto.getSourceHubId());
        HubEntity dest = requireHub(dto.getDestinationHubId());
        hubRouteRepository.findBySourceHub_IdAndDestinationHub_IdAndIsActiveTrue(dto.getSourceHubId(), dto.getDestinationHubId())
                .ifPresent(r -> {
                    throw new BadRequestException("Active route already exists for this hub pair");
                });
        HubRouteEntity entity = new HubRouteEntity();
        entity.setSourceHub(source);
        entity.setDestinationHub(dest);
        entity.setPricePerKm(dto.getPricePerKm());
        entity.setFixedPrice(dto.getFixedPrice());
        entity.setIsActive(true);
        return toResponse(hubRouteRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<HubRouteResponseDTO> listAll() {
        return hubRouteRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public HubRouteResponseDTO update(Long id, HubRouteRequestDTO dto) {
        validateRoutePayload(dto);
        if (dto.getSourceHubId().equals(dto.getDestinationHubId())) {
            throw new BadRequestException("source_hub_id and destination_hub_id must differ");
        }
        HubRouteEntity entity = hubRouteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));
        HubEntity source = requireHub(dto.getSourceHubId());
        HubEntity dest = requireHub(dto.getDestinationHubId());
        hubRouteRepository.findBySourceHub_IdAndDestinationHub_IdAndIsActiveTrue(dto.getSourceHubId(), dto.getDestinationHubId())
                .ifPresent(other -> {
                    if (!other.getId().equals(id)) {
                        throw new BadRequestException("Active route already exists for this hub pair");
                    }
                });
        entity.setSourceHub(source);
        entity.setDestinationHub(dest);
        entity.setPricePerKm(dto.getPricePerKm());
        entity.setFixedPrice(dto.getFixedPrice());
        return toResponse(hubRouteRepository.save(entity));
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        HubRouteEntity entity = hubRouteRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found with id: " + id));
        entity.setIsActive(false);
        hubRouteRepository.save(entity);
    }

    private void validateRoutePayload(HubRouteRequestDTO dto) {
        if (dto.getPricePerKm() == null && dto.getFixedPrice() == null) {
            throw new BadRequestException("At least one of pricePerKm or fixedPrice is required");
        }
        if (dto.getPricePerKm() != null && dto.getPricePerKm() < 0) {
            throw new BadRequestException("pricePerKm cannot be negative");
        }
        if (dto.getFixedPrice() != null && dto.getFixedPrice() < 0) {
            throw new BadRequestException("fixedPrice cannot be negative");
        }
    }

    private HubEntity requireHub(Long hubId) {
        return hubRepository.findById(hubId)
                .orElseThrow(() -> new BadRequestException("hub_id does not exist: " + hubId));
    }

    private HubRouteResponseDTO toResponse(HubRouteEntity e) {
        HubRouteResponseDTO d = new HubRouteResponseDTO();
        d.setId(e.getId());
        d.setSourceHubId(e.getSourceHub() == null ? null : e.getSourceHub().getId());
        d.setDestinationHubId(e.getDestinationHub() == null ? null : e.getDestinationHub().getId());
        d.setPricePerKm(e.getPricePerKm());
        d.setFixedPrice(e.getFixedPrice());
        d.setIsActive(e.getIsActive());
        d.setCreatedAt(e.getCreatedAt());
        d.setUpdatedAt(e.getUpdatedAt());
        return d;
    }
}

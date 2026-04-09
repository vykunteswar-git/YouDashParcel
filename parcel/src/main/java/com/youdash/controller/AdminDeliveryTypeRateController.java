package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.DeliveryTypeRateDTO;
import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.entity.DeliveryTypeRateEntity;
import com.youdash.pricing.DeliveryScope;
import com.youdash.repository.DeliveryTypeRateRepository;
import com.youdash.repository.DeliveryTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/admin/pricing/delivery-type-rates")
public class AdminDeliveryTypeRateController {

    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;

    @Autowired
    private DeliveryTypeRateRepository deliveryTypeRateRepository;

    @PostMapping
    public ApiResponse<DeliveryTypeRateDTO> upsertRate(@RequestBody DeliveryTypeRateDTO dto) {
        ApiResponse<DeliveryTypeRateDTO> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getDeliveryTypeName() == null || dto.getDeliveryTypeName().trim().isEmpty()) {
                throw new RuntimeException("deliveryTypeName is required");
            }
            if (dto.getScope() == null || dto.getScope().trim().isEmpty()) {
                throw new RuntimeException("scope is required");
            }
            if (dto.getFee() == null || dto.getFee() < 0) {
                throw new RuntimeException("fee must be >= 0");
            }

            String typeName = dto.getDeliveryTypeName().trim().toUpperCase();
            DeliveryTypeEntity type = deliveryTypeRepository.findByNameIgnoreCaseAndActiveTrue(typeName)
                    .orElseThrow(() -> new RuntimeException("Invalid delivery type: " + typeName));

            DeliveryScope scope = DeliveryScope.valueOf(dto.getScope().trim().toUpperCase());

            DeliveryTypeRateEntity rate = deliveryTypeRateRepository.findByDeliveryTypeAndScopeAndActiveTrue(type, scope)
                    .orElseGet(DeliveryTypeRateEntity::new);

            rate.setDeliveryType(type);
            rate.setScope(scope);
            rate.setFee(BigDecimal.valueOf(dto.getFee()));
            rate.setDescription(dto.getDescription());
            rate.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());

            DeliveryTypeRateEntity saved = deliveryTypeRateRepository.save(rate);
            response.setData(map(saved));
            response.setMessage("Delivery type rate saved successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
        }
        return response;
    }

    private DeliveryTypeRateDTO map(DeliveryTypeRateEntity e) {
        DeliveryTypeRateDTO dto = new DeliveryTypeRateDTO();
        dto.setDeliveryTypeName(e.getDeliveryType() == null ? null : e.getDeliveryType().getName());
        dto.setScope(e.getScope() == null ? null : e.getScope().name());
        dto.setFee(e.getFee() == null ? 0.0 : e.getFee().doubleValue());
        dto.setDescription(e.getDescription());
        dto.setActive(e.getActive());
        return dto;
    }
}


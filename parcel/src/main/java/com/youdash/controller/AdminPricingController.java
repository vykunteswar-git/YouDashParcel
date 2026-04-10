package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminPricingConfigDTO;
import com.youdash.dto.DeliveryTypeDTO;
import com.youdash.dto.GstConfigDTO;
import com.youdash.dto.PlatformFeeDTO;
import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.entity.GstConfigEntity;
import com.youdash.entity.InCityRadiusConfigEntity;
import com.youdash.entity.PlatformFeeEntity;
import com.youdash.repository.DeliveryTypeRepository;
import com.youdash.repository.GstConfigRepository;
import com.youdash.repository.InCityRadiusConfigRepository;
import com.youdash.repository.PlatformFeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/pricing")
public class AdminPricingController {

    @Autowired
    private GstConfigRepository gstConfigRepository;

    @Autowired
    private PlatformFeeRepository platformFeeRepository;

    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;

    @Autowired
    private InCityRadiusConfigRepository inCityRadiusConfigRepository;

    @PostMapping("/gst")
    public ApiResponse<String> upsertGst(@RequestBody GstConfigDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (dto == null) {
                throw new RuntimeException("Request body is required");
            }

            BigDecimal gstPercent = resolveTotalGstPercent(dto);
            if (gstPercent == null) {
                throw new RuntimeException("gstPercent is required");
            }
            if (gstPercent.signum() < 0) {
                throw new RuntimeException("GST percent cannot be negative");
            }

            gstConfigRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg -> {
                cfg.setActive(false);
                gstConfigRepository.save(cfg);
            });

            GstConfigEntity cfg = new GstConfigEntity();
            cfg.setGstPercent(gstPercent);
            // Keep legacy columns populated for compatibility (split evenly).
            BigDecimal half = gstPercent.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            cfg.setCgstPercent(half);
            cfg.setSgstPercent(half);
            cfg.setActive(true);
            gstConfigRepository.save(cfg);

            response.setData("GST config saved");
            response.setMessage("GST config updated successfully");
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

    @PostMapping("/platform-fee")
    public ApiResponse<String> upsertPlatformFee(@RequestBody PlatformFeeDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getFee() == null) {
                throw new RuntimeException("fee is required");
            }
            if (dto.getFee() < 0) {
                throw new RuntimeException("fee cannot be negative");
            }

            platformFeeRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg -> {
                cfg.setActive(false);
                platformFeeRepository.save(cfg);
            });

            PlatformFeeEntity cfg = new PlatformFeeEntity();
            cfg.setFee(BigDecimal.valueOf(dto.getFee()));
            cfg.setActive(true);
            platformFeeRepository.save(cfg);

            response.setData("Platform fee saved");
            response.setMessage("Platform fee updated successfully");
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

    @GetMapping("/config")
    public ApiResponse<AdminPricingConfigDTO> getPricingConfig() {
        ApiResponse<AdminPricingConfigDTO> response = new ApiResponse<>();
        try {
            AdminPricingConfigDTO dto = new AdminPricingConfigDTO();

            gstConfigRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg -> {
                BigDecimal gstPercent = cfg.getGstPercent();
                if (gstPercent == null) {
                    gstPercent = nz(cfg.getCgstPercent()).add(nz(cfg.getSgstPercent()));
                }
                dto.setGstPercent(gstPercent.doubleValue());
            });

            platformFeeRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg ->
                    dto.setPlatformFee(nz(cfg.getFee()).doubleValue())
            );

            inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg ->
                    dto.setInCityRadiusKm(cfg.getRadiusKm() == null ? null : cfg.getRadiusKm().doubleValue())
            );

            response.setData(dto);
            response.setMessage("Pricing config fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @PutMapping("/config")
    public ApiResponse<AdminPricingConfigDTO> upsertPricingConfig(@RequestBody AdminPricingConfigDTO dto) {
        ApiResponse<AdminPricingConfigDTO> response = new ApiResponse<>();
        try {
            if (dto == null) throw new RuntimeException("Request body is required");

            if (dto.getGstPercent() != null) {
                if (dto.getGstPercent() < 0) throw new RuntimeException("gstPercent cannot be negative");
                GstConfigDTO gstDto = new GstConfigDTO();
                gstDto.setGstPercent(dto.getGstPercent());
                upsertGst(gstDto);
            }

            if (dto.getPlatformFee() != null) {
                if (dto.getPlatformFee() < 0) throw new RuntimeException("platformFee cannot be negative");
                PlatformFeeDTO pf = new PlatformFeeDTO();
                pf.setFee(dto.getPlatformFee());
                upsertPlatformFee(pf);
            }

            if (dto.getInCityRadiusKm() != null) {
                if (dto.getInCityRadiusKm() <= 0) throw new RuntimeException("inCityRadiusKm must be > 0");

                inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg -> {
                    cfg.setActive(false);
                    inCityRadiusConfigRepository.save(cfg);
                });

                InCityRadiusConfigEntity entity = new InCityRadiusConfigEntity();
                entity.setRadiusKm(BigDecimal.valueOf(dto.getInCityRadiusKm()));
                entity.setActive(true);
                inCityRadiusConfigRepository.save(entity);
            }

            // Return the latest snapshot after updates
            return getPricingConfig();
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
            return response;
        }
    }

    @GetMapping("/delivery-types")
    public ApiResponse<List<DeliveryTypeDTO>> getActiveDeliveryTypes() {
        ApiResponse<List<DeliveryTypeDTO>> response = new ApiResponse<>();
        try {
            List<DeliveryTypeDTO> dtos = deliveryTypeRepository.findByActiveTrueOrderByNameAsc().stream()
                    .map(this::mapDeliveryType)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Delivery types fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            response.setTotalCount(dtos.size());
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @PostMapping("/delivery-types")
    public ApiResponse<DeliveryTypeDTO> createOrUpdateDeliveryType(@RequestBody DeliveryTypeDTO dto) {
        ApiResponse<DeliveryTypeDTO> response = new ApiResponse<>();
        try {
            if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
                throw new RuntimeException("name is required");
            }
            if (dto.getFee() == null || dto.getFee() < 0) {
                throw new RuntimeException("fee must be >= 0");
            }

            String name = dto.getName().trim().toUpperCase();
            DeliveryTypeEntity entity = deliveryTypeRepository.findAll().stream()
                    .filter(d -> d.getName() != null && d.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .orElseGet(DeliveryTypeEntity::new);

            entity.setName(name);
            entity.setFee(BigDecimal.valueOf(dto.getFee()));
            entity.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());

            DeliveryTypeEntity saved = deliveryTypeRepository.save(entity);
            response.setData(mapDeliveryType(saved));
            response.setMessage("Delivery type saved successfully");
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

    private DeliveryTypeDTO mapDeliveryType(DeliveryTypeEntity entity) {
        DeliveryTypeDTO dto = new DeliveryTypeDTO();
        dto.setName(entity.getName());
        dto.setFee(entity.getFee() == null ? 0.0 : entity.getFee().doubleValue());
        dto.setActive(entity.getActive());
        return dto;
    }

    private static BigDecimal resolveTotalGstPercent(GstConfigDTO dto) {
        if (dto == null) return null;
        if (dto.getGstPercent() != null) return BigDecimal.valueOf(dto.getGstPercent());
        if (dto.getCgstPercent() != null && dto.getSgstPercent() != null) {
            return BigDecimal.valueOf(dto.getCgstPercent()).add(BigDecimal.valueOf(dto.getSgstPercent()));
        }
        return null;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}


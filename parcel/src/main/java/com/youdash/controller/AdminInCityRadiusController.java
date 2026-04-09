package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.InCityRadiusConfigDTO;
import com.youdash.entity.InCityRadiusConfigEntity;
import com.youdash.repository.InCityRadiusConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/admin/pricing/in-city-radius")
public class AdminInCityRadiusController {

    @Autowired
    private InCityRadiusConfigRepository inCityRadiusConfigRepository;

    @GetMapping
    public ApiResponse<InCityRadiusConfigDTO> getActiveRadius() {
        ApiResponse<InCityRadiusConfigDTO> response = new ApiResponse<>();
        try {
            InCityRadiusConfigDTO dto = new InCityRadiusConfigDTO();
            inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg -> {
                dto.setRadiusKm(cfg.getRadiusKm() == null ? null : cfg.getRadiusKm().doubleValue());
                dto.setActive(cfg.getActive());
            });
            if (dto.getRadiusKm() == null) {
                dto.setRadiusKm(60.0);
                dto.setActive(true);
            }
            response.setData(dto);
            response.setMessage("In-city radius fetched successfully");
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

    @PostMapping
    public ApiResponse<String> createRadius(@RequestBody InCityRadiusConfigDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            validate(dto);

            inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc().ifPresent(cfg -> {
                cfg.setActive(false);
                inCityRadiusConfigRepository.save(cfg);
            });

            InCityRadiusConfigEntity entity = new InCityRadiusConfigEntity();
            entity.setRadiusKm(BigDecimal.valueOf(dto.getRadiusKm()));
            entity.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());
            inCityRadiusConfigRepository.save(entity);

            response.setData("In-city radius saved");
            response.setMessage("In-city radius created successfully");
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

    @PutMapping
    public ApiResponse<String> updateActiveRadius(@RequestBody InCityRadiusConfigDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            validate(dto);

            InCityRadiusConfigEntity entity = inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                    .orElseGet(InCityRadiusConfigEntity::new);
            entity.setRadiusKm(BigDecimal.valueOf(dto.getRadiusKm()));
            entity.setActive(dto.getActive() == null ? Boolean.TRUE : dto.getActive());
            inCityRadiusConfigRepository.save(entity);

            response.setData("In-city radius updated");
            response.setMessage("In-city radius updated successfully");
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

    private void validate(InCityRadiusConfigDTO dto) {
        if (dto == null || dto.getRadiusKm() == null) {
            throw new RuntimeException("radiusKm is required");
        }
        if (dto.getRadiusKm() <= 0) {
            throw new RuntimeException("radiusKm must be > 0");
        }
    }
}


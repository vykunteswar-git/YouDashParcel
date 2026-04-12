package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppConfigDTO;
import com.youdash.entity.AppConfigEntity;
import com.youdash.repository.AppConfigRepository;
import com.youdash.service.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AppConfigServiceImpl implements AppConfigService {

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Override
    public ApiResponse<AppConfigDTO> getConfig() {
        ApiResponse<AppConfigDTO> response = new ApiResponse<>();
        try {
            AppConfigEntity e = appConfigRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Config not found"));
            response.setData(toDto(e));
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<AppConfigDTO> updateConfig(AppConfigDTO dto) {
        ApiResponse<AppConfigDTO> response = new ApiResponse<>();
        try {
            AppConfigEntity e = appConfigRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Config not found"));
            if (dto.getGstPercent() != null) {
                e.setGstPercent(dto.getGstPercent());
            }
            if (dto.getPlatformFee() != null) {
                e.setPlatformFee(dto.getPlatformFee());
            }
            if (dto.getPickupRatePerKm() != null) {
                e.setPickupRatePerKm(dto.getPickupRatePerKm());
            }
            if (dto.getDropRatePerKm() != null) {
                e.setDropRatePerKm(dto.getDropRatePerKm());
            }
            if (dto.getPerKgRate() != null) {
                e.setPerKgRate(dto.getPerKgRate());
            }
            if (dto.getDefaultRouteRatePerKm() != null) {
                e.setDefaultRouteRatePerKm(dto.getDefaultRouteRatePerKm());
            }
            AppConfigEntity saved = appConfigRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Config updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private static AppConfigDTO toDto(AppConfigEntity e) {
        AppConfigDTO d = new AppConfigDTO();
        d.setId(e.getId());
        d.setGstPercent(e.getGstPercent());
        d.setPlatformFee(e.getPlatformFee());
        d.setPickupRatePerKm(e.getPickupRatePerKm());
        d.setDropRatePerKm(e.getDropRatePerKm());
        d.setPerKgRate(e.getPerKgRate());
        d.setDefaultRouteRatePerKm(e.getDefaultRouteRatePerKm());
        return d;
    }

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

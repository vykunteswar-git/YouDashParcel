package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppConfigDTO;
import com.youdash.dto.CheckoutPaymentOptionsDTO;
import com.youdash.entity.AppConfigEntity;
import com.youdash.model.PaymentType;
import com.youdash.repository.AppConfigRepository;
import com.youdash.service.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
            if (dto.getCodEnabled() != null) {
                e.setCodEnabled(dto.getCodEnabled());
            }
            if (dto.getOnlineEnabled() != null) {
                e.setOnlineEnabled(dto.getOnlineEnabled());
            }
            if (dto.getDefaultPaymentType() != null) {
                e.setDefaultPaymentType(dto.getDefaultPaymentType());
            }
            validatePaymentToggleCombination(e);
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

    @Override
    public ApiResponse<CheckoutPaymentOptionsDTO> getCheckoutPaymentOptions() {
        ApiResponse<CheckoutPaymentOptionsDTO> response = new ApiResponse<>();
        try {
            AppConfigEntity e = appConfigRepository.findById(1L)
                    .orElseThrow(() -> new RuntimeException("Config not found"));
            List<PaymentType> available = resolveAvailablePaymentTypes(e.getCodEnabled(), e.getOnlineEnabled());
            PaymentType defaultType = e.getDefaultPaymentType();
            if (defaultType == null || !available.contains(defaultType)) {
                defaultType = available.isEmpty() ? null : available.get(0);
            }
            CheckoutPaymentOptionsDTO data = CheckoutPaymentOptionsDTO.builder()
                    .codEnabled(Boolean.TRUE.equals(e.getCodEnabled()))
                    .onlineEnabled(Boolean.TRUE.equals(e.getOnlineEnabled()))
                    .defaultPaymentType(defaultType)
                    .availablePaymentTypes(available)
                    .build();
            response.setData(data);
            response.setMessage("OK");
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
        d.setCodEnabled(e.getCodEnabled());
        d.setOnlineEnabled(e.getOnlineEnabled());
        d.setDefaultPaymentType(e.getDefaultPaymentType());
        return d;
    }

    private static List<PaymentType> resolveAvailablePaymentTypes(Boolean codEnabled, Boolean onlineEnabled) {
        List<PaymentType> available = new ArrayList<>(2);
        if (Boolean.TRUE.equals(codEnabled)) {
            available.add(PaymentType.COD);
        }
        if (Boolean.TRUE.equals(onlineEnabled)) {
            available.add(PaymentType.ONLINE);
        }
        return available;
    }

    private static void validatePaymentToggleCombination(AppConfigEntity e) {
        List<PaymentType> available = resolveAvailablePaymentTypes(e.getCodEnabled(), e.getOnlineEnabled());
        if (available.isEmpty()) {
            throw new RuntimeException("At least one payment mode must remain enabled");
        }
        if (e.getDefaultPaymentType() != null && !available.contains(e.getDefaultPaymentType())) {
            throw new RuntimeException("defaultPaymentType must be one of enabled payment modes");
        }
    }

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppVersionDTO;
import com.youdash.dto.VersionCheckResponseDTO;
import com.youdash.entity.AppVersionEntity;
import com.youdash.repository.AppVersionRepository;
import com.youdash.service.AppVersionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AppVersionServiceImpl implements AppVersionService {

    @Autowired
    private AppVersionRepository appVersionRepository;

    private AppVersionEntity getOrCreate() {
        return appVersionRepository.findById(1L).orElseGet(() -> {
            AppVersionEntity e = new AppVersionEntity();
            e.setId(1L);
            e.setUserVersionCode(1);
            e.setUserPlayStoreUrl("");
            e.setRiderVersionCode(1);
            e.setRiderPlayStoreUrl("");
            return appVersionRepository.save(e);
        });
    }

    @Override
    public ApiResponse<AppVersionDTO> getConfig() {
        ApiResponse<AppVersionDTO> response = new ApiResponse<>();
        try {
            AppVersionEntity e = getOrCreate();
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
    public ApiResponse<AppVersionDTO> updateConfig(AppVersionDTO dto) {
        ApiResponse<AppVersionDTO> response = new ApiResponse<>();
        try {
            AppVersionEntity e = getOrCreate();
            if (dto.getUserVersionCode() != null) e.setUserVersionCode(dto.getUserVersionCode());
            if (dto.getUserPlayStoreUrl() != null) e.setUserPlayStoreUrl(dto.getUserPlayStoreUrl());
            if (dto.getRiderVersionCode() != null) e.setRiderVersionCode(dto.getRiderVersionCode());
            if (dto.getRiderPlayStoreUrl() != null) e.setRiderPlayStoreUrl(dto.getRiderPlayStoreUrl());
            AppVersionEntity saved = appVersionRepository.save(e);
            response.setData(toDto(saved));
            response.setMessage("Version config updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<VersionCheckResponseDTO> checkVersion(String appType, Integer versionCode) {
        ApiResponse<VersionCheckResponseDTO> response = new ApiResponse<>();
        try {
            AppVersionEntity e = getOrCreate();
            VersionCheckResponseDTO result = new VersionCheckResponseDTO();

            if ("RIDER".equalsIgnoreCase(appType)) {
                int required = e.getRiderVersionCode() != null ? e.getRiderVersionCode() : 1;
                result.setUpdateRequired(versionCode == null || versionCode.intValue() != required);
                result.setPlayStoreUrl(e.getRiderPlayStoreUrl());
            } else {
                int required = e.getUserVersionCode() != null ? e.getUserVersionCode() : 1;
                result.setUpdateRequired(versionCode == null || versionCode.intValue() != required);
                result.setPlayStoreUrl(e.getUserPlayStoreUrl());
            }

            response.setData(result);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception ex) {
            setError(response, ex.getMessage());
        }
        return response;
    }

    private static AppVersionDTO toDto(AppVersionEntity e) {
        AppVersionDTO d = new AppVersionDTO();
        d.setId(e.getId());
        d.setUserVersionCode(e.getUserVersionCode());
        d.setUserPlayStoreUrl(e.getUserPlayStoreUrl());
        d.setRiderVersionCode(e.getRiderVersionCode());
        d.setRiderPlayStoreUrl(e.getRiderPlayStoreUrl());
        return d;
    }

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

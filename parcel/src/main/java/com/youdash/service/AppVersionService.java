package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppVersionDTO;
import com.youdash.dto.VersionCheckResponseDTO;

public interface AppVersionService {
    ApiResponse<AppVersionDTO> getConfig();
    ApiResponse<AppVersionDTO> updateConfig(AppVersionDTO dto);
    ApiResponse<VersionCheckResponseDTO> checkVersion(String appType, Integer versionCode);
}

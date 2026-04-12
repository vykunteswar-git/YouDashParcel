package com.youdash.service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppConfigDTO;

public interface AppConfigService {

    ApiResponse<AppConfigDTO> getConfig();

    ApiResponse<AppConfigDTO> updateConfig(AppConfigDTO dto);
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppConfigDTO;
import com.youdash.service.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/config")
public class AdminAppConfigController {

    @Autowired
    private AppConfigService appConfigService;

    @GetMapping
    public ApiResponse<AppConfigDTO> get() {
        return appConfigService.getConfig();
    }

    @PutMapping
    public ApiResponse<AppConfigDTO> update(@RequestBody AppConfigDTO dto) {
        return appConfigService.updateConfig(dto);
    }
}

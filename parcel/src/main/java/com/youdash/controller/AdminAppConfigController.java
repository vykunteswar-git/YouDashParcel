package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppConfigDTO;
import com.youdash.service.AppConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/config")
@Tag(name = "Admin — App Config", description = "Global pricing and checkout configuration.")
public class AdminAppConfigController {

    @Autowired
    private AppConfigService appConfigService;

    @GetMapping
    @Operation(summary = "Get app config")
    public ApiResponse<AppConfigDTO> get() {
        return appConfigService.getConfig();
    }

    @PutMapping
    @Operation(summary = "Update app config")
    public ApiResponse<AppConfigDTO> update(@RequestBody AppConfigDTO dto) {
        return appConfigService.updateConfig(dto);
    }
}

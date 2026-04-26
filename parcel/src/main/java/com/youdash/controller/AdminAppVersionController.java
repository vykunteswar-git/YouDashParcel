package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AppVersionDTO;
import com.youdash.service.AppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/app-version")
@Tag(name = "Admin — App Version", description = "Manage app version codes and Play Store URLs.")
public class AdminAppVersionController {

    @Autowired
    private AppVersionService appVersionService;

    @GetMapping
    @Operation(summary = "Get app version config")
    public ApiResponse<AppVersionDTO> get() {
        return appVersionService.getConfig();
    }

    @PutMapping
    @Operation(summary = "Update app version config")
    public ApiResponse<AppVersionDTO> update(@RequestBody AppVersionDTO dto) {
        return appVersionService.updateConfig(dto);
    }
}

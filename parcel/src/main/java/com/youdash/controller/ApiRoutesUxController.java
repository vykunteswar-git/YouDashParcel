package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RoutePreviewRequestDTO;
import com.youdash.dto.RoutePreviewResponseDTO;
import com.youdash.service.LogisticsUxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/routes")
@Tag(name = "Routes (UX)", description = "Preview and serviceable network")
public class ApiRoutesUxController {

    @Autowired
    private LogisticsUxService logisticsUxService;

    @PostMapping("/preview")
    @Operation(summary = "Hub city chain / incity label for pickup→drop")
    public ApiResponse<RoutePreviewResponseDTO> preview(@RequestBody RoutePreviewRequestDTO dto) {
        return logisticsUxService.previewRoute(dto);
    }

    @GetMapping("/serviceable-cities")
    @Operation(summary = "Distinct cities with an active hub")
    public ApiResponse<List<String>> serviceableCities() {
        return logisticsUxService.listServiceableCities();
    }
}

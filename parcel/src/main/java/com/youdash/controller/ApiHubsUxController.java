package com.youdash.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.NearestHubResponseDTO;
import com.youdash.service.LogisticsUxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/hubs")
@Tag(name = "Hubs (UX)", description = "Client helpers around hubs")
public class ApiHubsUxController {

    @Autowired
    private LogisticsUxService logisticsUxService;

    @GetMapping("/nearest")
    @Operation(summary = "Nearest active hub by coordinates")
    public ApiResponse<NearestHubResponseDTO> nearest(
            @RequestParam double lat,
            @RequestParam double lng) {
        return logisticsUxService.nearestHub(lat, lng);
    }
}

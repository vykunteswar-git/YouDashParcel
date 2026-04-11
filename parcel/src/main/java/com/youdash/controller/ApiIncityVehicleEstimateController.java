package com.youdash.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.IncityVehicleEstimateRequestDTO;
import com.youdash.dto.VehiclePriceEstimateDTO;
import com.youdash.service.LogisticsUxService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/incity/vehicles")
@Tag(name = "Incity vehicles (UX)", description = "Compare fares before booking")
public class ApiIncityVehicleEstimateController {

    @Autowired
    private LogisticsUxService logisticsUxService;

    @PostMapping("/estimate")
    @Operation(summary = "Price estimate per active vehicle (same zone only)")
    public ApiResponse<List<VehiclePriceEstimateDTO>> estimate(@RequestBody IncityVehicleEstimateRequestDTO dto) {
        return logisticsUxService.estimateIncityVehicles(dto);
    }
}

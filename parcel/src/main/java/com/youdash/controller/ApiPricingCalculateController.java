package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PricingCalculateRequestDTO;
import com.youdash.dto.PricingCalculateResponseDTO;
import com.youdash.service.PricingCalculateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
@Tag(name = "Pricing calculate", description = "Unified fare: zones, vehicles, hubs, routes, weight, config")
public class ApiPricingCalculateController {

    @Autowired
    private PricingCalculateService pricingCalculateService;

    @PostMapping("/calculate")
    @Operation(summary = "Calculate price for incity or outstation")
    public ApiResponse<PricingCalculateResponseDTO> calculate(@Valid @RequestBody PricingCalculateRequestDTO dto) {
        return pricingCalculateService.calculate(dto);
    }
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ServiceAvailabilityRequestDTO;
import com.youdash.dto.ServiceAvailabilityResponseDTO;
import com.youdash.service.ServiceAvailabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/service")
@Tag(name = "Service availability", description = "Single call: incity/outstation, vehicles/hubs, delivery types")
public class ApiServiceAvailabilityController {

    @Autowired
    private ServiceAvailabilityService serviceAvailabilityService;

    @PostMapping("/availability")
    @Operation(summary = "Resolve service mode and eligible options for pickup/drop")
    public ApiResponse<ServiceAvailabilityResponseDTO> availability(@Valid @RequestBody ServiceAvailabilityRequestDTO dto) {
        return serviceAvailabilityService.check(dto);
    }
}

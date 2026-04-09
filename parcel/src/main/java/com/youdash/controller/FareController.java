package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FareCalculateRequestDTO;
import com.youdash.dto.FareCalculateResponseDTO;
import com.youdash.service.FareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/public")
public class FareController {

    @Autowired
    private FareService fareService;

    @PostMapping("/fare/calculate")
    public ApiResponse<FareCalculateResponseDTO> calculateFare(@RequestBody FareCalculateRequestDTO dto) {
        return fareService.calculateFare(dto);
    }
}


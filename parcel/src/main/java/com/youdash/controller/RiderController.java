package com.youdash.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.service.RiderService;

@RestController
@RequestMapping("/riders")
public class RiderController {

    @Autowired
    private RiderService riderService;

    @PostMapping
    public ApiResponse<RiderResponseDTO> createRider(@RequestBody RiderRequestDTO dto) {
        return riderService.createRider(dto);
    }

    @GetMapping
    public ApiResponse<List<RiderResponseDTO>> getAllRiders() {
        return riderService.getAllRiders();
    }

    @GetMapping("/available")
    public ApiResponse<List<RiderResponseDTO>> getAvailableRiders() {
        return riderService.getAvailableRiders();
    }

    @PutMapping("/{id}/availability")
    public ApiResponse<RiderResponseDTO> updateAvailability(
            @PathVariable Long id, 
            @RequestBody Map<String, Boolean> statusMap) {
        Boolean isAvailable = statusMap.get("isAvailable");
        if (isAvailable == null) {
            throw new RuntimeException("isAvailable is required");
        }
        return riderService.updateAvailability(id, isAvailable);
    }

    @PutMapping("/{id}/location")
    public ApiResponse<RiderResponseDTO> updateLocation(
            @PathVariable Long id, 
            @RequestBody Map<String, Double> locationMap) {
        Double lat = locationMap.get("lat");
        Double lng = locationMap.get("lng");
        if (lat == null || lng == null) {
            throw new RuntimeException("lat and lng are required");
        }
        return riderService.updateLocation(id, lat, lng);
    }
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRequestDTO;
import com.youdash.dto.ZoneResponseDTO;
import com.youdash.service.ZoneService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/zones")
public class AdminZoneController {

    @Autowired
    private ZoneService zoneService;

    @PostMapping
    public ApiResponse<ZoneResponseDTO> createZone(@RequestBody ZoneRequestDTO dto) {
        return zoneService.createZone(dto);
    }

    @GetMapping
    public ApiResponse<List<ZoneResponseDTO>> listZones() {
        return zoneService.listZones();
    }

    @PutMapping("/{id}")
    public ApiResponse<ZoneResponseDTO> updateZone(@PathVariable Long id, @RequestBody ZoneRequestDTO dto) {
        return zoneService.updateZone(id, dto);
    }
}

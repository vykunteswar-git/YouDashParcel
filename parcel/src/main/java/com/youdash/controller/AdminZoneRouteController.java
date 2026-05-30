package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRouteRequestDTO;
import com.youdash.dto.ZoneRouteResponseDTO;
import com.youdash.service.ZoneRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/zone-routes")
public class AdminZoneRouteController {

    @Autowired
    private ZoneRouteService zoneRouteService;

    @PostMapping
    public ApiResponse<ZoneRouteResponseDTO> create(@RequestBody ZoneRouteRequestDTO dto) {
        return zoneRouteService.create(dto);
    }

    @GetMapping
    public ApiResponse<List<ZoneRouteResponseDTO>> list() {
        return zoneRouteService.list();
    }

    @PutMapping("/{id}")
    public ApiResponse<ZoneRouteResponseDTO> update(@PathVariable Long id, @RequestBody ZoneRouteRequestDTO dto) {
        return zoneRouteService.update(id, dto);
    }
}

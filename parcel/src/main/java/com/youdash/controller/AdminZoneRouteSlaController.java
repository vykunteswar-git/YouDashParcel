package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.ZoneRouteSLARequestDTO;
import com.youdash.dto.ZoneRouteSLAResponseDTO;
import com.youdash.service.ZoneRouteSlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/zone-route-sla")
public class AdminZoneRouteSlaController {

    @Autowired
    private ZoneRouteSlaService zoneRouteSlaService;

    @PostMapping
    public ApiResponse<ZoneRouteSLAResponseDTO> create(@RequestBody ZoneRouteSLARequestDTO dto) {
        return zoneRouteSlaService.create(dto);
    }

    @GetMapping
    public ApiResponse<List<ZoneRouteSLAResponseDTO>> list(@RequestParam Long zoneRouteId) {
        return zoneRouteSlaService.listByZoneRouteId(zoneRouteId);
    }

    @PutMapping("/{id}")
    public ApiResponse<ZoneRouteSLAResponseDTO> update(@PathVariable Long id, @RequestBody ZoneRouteSLARequestDTO dto) {
        return zoneRouteSlaService.update(id, dto);
    }
}

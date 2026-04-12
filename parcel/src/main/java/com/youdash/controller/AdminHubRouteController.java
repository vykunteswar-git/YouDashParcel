package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteRequestDTO;
import com.youdash.dto.HubRouteResponseDTO;
import com.youdash.service.HubRouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hub-routes")
public class AdminHubRouteController {

    @Autowired
    private HubRouteService hubRouteService;

    @PostMapping
    public ApiResponse<HubRouteResponseDTO> create(@RequestBody HubRouteRequestDTO dto) {
        return hubRouteService.create(dto);
    }

    @GetMapping
    public ApiResponse<List<HubRouteResponseDTO>> list() {
        return hubRouteService.list();
    }

    @PutMapping("/{id}")
    public ApiResponse<HubRouteResponseDTO> update(@PathVariable Long id, @RequestBody HubRouteRequestDTO dto) {
        return hubRouteService.update(id, dto);
    }
}

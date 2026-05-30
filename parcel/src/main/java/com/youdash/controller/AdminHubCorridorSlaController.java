package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubCorridorSlaRequestDTO;
import com.youdash.dto.HubCorridorSlaResponseDTO;
import com.youdash.service.HubCorridorSlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hub-corridor-sla")
public class AdminHubCorridorSlaController {

    @Autowired
    private HubCorridorSlaService hubCorridorSlaService;

    @PostMapping
    public ApiResponse<HubCorridorSlaResponseDTO> create(@RequestBody HubCorridorSlaRequestDTO dto) {
        return hubCorridorSlaService.create(dto);
    }

    @GetMapping
    public ApiResponse<List<HubCorridorSlaResponseDTO>> list(@RequestParam Long hubId) {
        return hubCorridorSlaService.listByHubId(hubId);
    }

    @PutMapping("/{id}")
    public ApiResponse<HubCorridorSlaResponseDTO> update(
            @PathVariable Long id, @RequestBody HubCorridorSlaRequestDTO dto) {
        return hubCorridorSlaService.update(id, dto);
    }
}

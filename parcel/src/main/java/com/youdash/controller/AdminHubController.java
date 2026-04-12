package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRequestDTO;
import com.youdash.dto.HubResponseDTO;
import com.youdash.service.HubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hubs")
public class AdminHubController {

    @Autowired
    private HubService hubService;

    @PostMapping
    public ApiResponse<HubResponseDTO> create(@RequestBody HubRequestDTO dto) {
        return hubService.create(dto);
    }

    @GetMapping
    public ApiResponse<List<HubResponseDTO>> list() {
        return hubService.list();
    }

    @PutMapping("/{id}")
    public ApiResponse<HubResponseDTO> update(@PathVariable Long id, @RequestBody HubRequestDTO dto) {
        return hubService.update(id, dto);
    }
}

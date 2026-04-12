package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteSLARequestDTO;
import com.youdash.dto.HubRouteSLAResponseDTO;
import com.youdash.service.HubRouteSlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/hub-route-sla")
public class AdminHubRouteSLAController {

    @Autowired
    private HubRouteSlaService hubRouteSlaService;

    @PostMapping
    public ApiResponse<HubRouteSLAResponseDTO> create(@RequestBody HubRouteSLARequestDTO dto) {
        return hubRouteSlaService.create(dto);
    }

    @GetMapping
    public ApiResponse<List<HubRouteSLAResponseDTO>> list(@RequestParam Long hubRouteId) {
        return hubRouteSlaService.listByHubRouteId(hubRouteId);
    }

    @PutMapping("/{id}")
    public ApiResponse<HubRouteSLAResponseDTO> update(@PathVariable Long id, @RequestBody HubRouteSLARequestDTO dto) {
        return hubRouteSlaService.update(id, dto);
    }
}

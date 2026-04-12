package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubRouteSlaPreviewResponseDTO;
import com.youdash.service.HubRouteSlaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customer")
public class CustomerHubRouteSlaController {

    @Autowired
    private HubRouteSlaService hubRouteSlaService;

    /**
     * Human-readable delivery promises for a hub pair route (active SLAs only).
     */
    @GetMapping("/hub-route-sla-preview")
    public ApiResponse<HubRouteSlaPreviewResponseDTO> preview(@RequestParam Long hubRouteId) {
        return hubRouteSlaService.preview(hubRouteId);
    }
}

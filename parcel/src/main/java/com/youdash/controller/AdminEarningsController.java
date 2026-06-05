package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.admin.AdminEarningsDTO;
import com.youdash.service.AdminEarningsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/earnings")
public class AdminEarningsController {

    @Autowired
    private AdminEarningsService adminEarningsService;

    @GetMapping
    public ApiResponse<AdminEarningsDTO> getEarnings(
            @RequestParam(name = "range", required = false, defaultValue = "THIS_WEEK") String range) {
        return adminEarningsService.getEarnings(range);
    }
}

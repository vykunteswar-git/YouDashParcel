package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.AdminLoginDTO;
import com.youdash.dto.AdminResponseDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.service.AdminService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @PostMapping("/login")
    public ApiResponse<AdminResponseDTO> login(@RequestBody AdminLoginDTO dto) {
        return adminService.login(dto);
    }

    @PostMapping("/vehicles")
    public ApiResponse<VehicleDTO> createVehicle(@RequestBody VehicleDTO dto) {
        return adminService.createVehicle(dto);
    }

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleDTO>> getAllVehicles() {
        return adminService.getAllVehicles();
    }

    @PutMapping("/vehicles/{id}")
    public ApiResponse<VehicleDTO> updateVehicle(@PathVariable Long id, @RequestBody VehicleDTO dto) {
        return adminService.updateVehicle(id, dto);
    }
}

package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PackageItemResponseDTO;
import com.youdash.service.PackageItemService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/package-items")
public class PackageItemController {

    @Autowired
    private PackageItemService packageItemService;

    @GetMapping("/{categoryId}")
    public ApiResponse<List<PackageItemResponseDTO>> getItemsByCategoryId(@PathVariable Long categoryId) {
        return packageItemService.getItemsByCategoryId(categoryId);
    }
}

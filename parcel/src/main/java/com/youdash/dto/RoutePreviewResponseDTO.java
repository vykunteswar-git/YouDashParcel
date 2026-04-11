package com.youdash.dto;

import java.util.List;

import lombok.Data;

@Data
public class RoutePreviewResponseDTO {
    private String serviceMode;
    private List<String> cities;
    private List<Long> hubPathIds;
}

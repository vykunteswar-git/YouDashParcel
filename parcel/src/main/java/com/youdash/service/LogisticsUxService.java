package com.youdash.service;

import java.util.List;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.IncityVehicleEstimateRequestDTO;
import com.youdash.dto.NearestHubResponseDTO;
import com.youdash.dto.RoutePreviewRequestDTO;
import com.youdash.dto.RoutePreviewResponseDTO;
import com.youdash.dto.VehiclePriceEstimateDTO;

public interface LogisticsUxService {

    ApiResponse<RoutePreviewResponseDTO> previewRoute(RoutePreviewRequestDTO dto);

    ApiResponse<List<String>> listServiceableCities();

    ApiResponse<NearestHubResponseDTO> nearestHub(double lat, double lng);

    ApiResponse<List<VehiclePriceEstimateDTO>> estimateIncityVehicles(IncityVehicleEstimateRequestDTO dto);
}

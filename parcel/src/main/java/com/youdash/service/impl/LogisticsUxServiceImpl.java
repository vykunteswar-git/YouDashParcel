package com.youdash.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.IncityVehicleEstimateRequestDTO;
import com.youdash.dto.NearestHubResponseDTO;
import com.youdash.dto.PricingCalculateRequestDTO;
import com.youdash.dto.PricingCalculateResponseDTO;
import com.youdash.dto.RoutePreviewRequestDTO;
import com.youdash.dto.RoutePreviewResponseDTO;
import com.youdash.dto.VehiclePriceEstimateDTO;
import com.youdash.entity.HubEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.ZoneEntity;
import com.youdash.model.FulfillmentType;
import com.youdash.repository.HubRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.LogisticsUxService;
import com.youdash.service.PricingCalculateService;
import com.youdash.service.ZoneGeoService;
import com.youdash.util.GeoDistanceUtil;

@Service
public class LogisticsUxServiceImpl implements LogisticsUxService {

    @Autowired
    private PricingCalculateService pricingCalculateService;

    @Autowired
    private ZoneGeoService zoneGeoService;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Override
    public ApiResponse<RoutePreviewResponseDTO> previewRoute(RoutePreviewRequestDTO dto) {
        ApiResponse<RoutePreviewResponseDTO> response = new ApiResponse<>();
        try {
            if (dto == null
                    || dto.getPickupLat() == null || dto.getPickupLng() == null
                    || dto.getDropLat() == null || dto.getDropLng() == null) {
                throw new RuntimeException("pickupLat, pickupLng, dropLat, dropLng are required");
            }
            PricingCalculateRequestDTO p = new PricingCalculateRequestDTO();
            p.setPickupLat(dto.getPickupLat());
            p.setPickupLng(dto.getPickupLng());
            p.setDropLat(dto.getDropLat());
            p.setDropLng(dto.getDropLng());
            p.setWeightKg(0.0);
            if (zoneGeoService.isSameIncityZone(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng())) {
                VehicleEntity v = vehicleRepository.findByIsActiveTrue().stream().findFirst()
                        .orElseThrow(() -> new RuntimeException("No active vehicle for incity preview"));
                p.setVehicleId(v.getId());
            } else {
                p.setDeliveryOption(FulfillmentType.DOOR_TO_DOOR);
            }
            PricingCalculateResponseDTO calc = pricingCalculateService.computePricing(p);
            RoutePreviewResponseDTO out = new RoutePreviewResponseDTO();
            out.setServiceMode(calc.getServiceMode());
            if ("INCITY".equalsIgnoreCase(calc.getServiceMode())) {
                List<String> cities = new ArrayList<>();
                zoneGeoService.findZoneContaining(dto.getPickupLat(), dto.getPickupLng()).map(ZoneEntity::getCity).ifPresent(cities::add);
                if (cities.isEmpty()) {
                    cities.add("Incity");
                }
                out.setCities(cities);
                out.setHubPathIds(new ArrayList<>());
            } else {
                out.setCities(calc.getRoute() != null ? calc.getRoute() : new ArrayList<>());
                out.setHubPathIds(calc.getHubPathIds() != null ? calc.getHubPathIds() : new ArrayList<>());
            }
            response.setData(out);
            response.setMessage("Route preview OK");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<String>> listServiceableCities() {
        ApiResponse<List<String>> response = new ApiResponse<>();
        try {
            List<String> cities = hubRepository.findByIsActiveTrue().stream()
                    .map(HubEntity::getCity)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .collect(Collectors.toList());
            response.setData(cities);
            response.setMessage("Serviceable cities fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<NearestHubResponseDTO> nearestHub(double lat, double lng) {
        ApiResponse<NearestHubResponseDTO> response = new ApiResponse<>();
        try {
            List<HubEntity> hubs = hubRepository.findByIsActiveTrue();
            if (hubs.isEmpty()) {
                throw new RuntimeException("No active hubs");
            }
            HubEntity best = hubs.stream()
                    .min(Comparator.comparingDouble(h ->
                            GeoDistanceUtil.haversineKm(lat, lng, h.getLat(), h.getLng())))
                    .orElseThrow();
            double d = GeoDistanceUtil.haversineKm(lat, lng, best.getLat(), best.getLng());
            NearestHubResponseDTO dto = new NearestHubResponseDTO();
            dto.setHubId(best.getId());
            dto.setCity(best.getCity());
            dto.setName(best.getName());
            dto.setLat(best.getLat());
            dto.setLng(best.getLng());
            dto.setDistanceKm(Math.round(d * 100.0) / 100.0);
            response.setData(dto);
            response.setMessage("Nearest hub resolved");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<VehiclePriceEstimateDTO>> estimateIncityVehicles(IncityVehicleEstimateRequestDTO dto) {
        ApiResponse<List<VehiclePriceEstimateDTO>> response = new ApiResponse<>();
        try {
            if (dto == null
                    || dto.getPickupLat() == null || dto.getPickupLng() == null
                    || dto.getDropLat() == null || dto.getDropLng() == null) {
                throw new RuntimeException("pickupLat, pickupLng, dropLat, dropLng are required");
            }
            if (!zoneGeoService.isSameIncityZone(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng())) {
                throw new RuntimeException("Pickup and drop must be in the same incity zone");
            }
            double w = dto.getWeightKg() == null || dto.getWeightKg() < 0 ? 0.0 : dto.getWeightKg();
            List<VehiclePriceEstimateDTO> rows = new ArrayList<>();
            for (VehicleEntity vehicle : vehicleRepository.findByIsActiveTrue()) {
                PricingCalculateRequestDTO p = new PricingCalculateRequestDTO();
                p.setPickupLat(dto.getPickupLat());
                p.setPickupLng(dto.getPickupLng());
                p.setDropLat(dto.getDropLat());
                p.setDropLng(dto.getDropLng());
                p.setVehicleId(vehicle.getId());
                p.setWeightKg(w);
                try {
                    PricingCalculateResponseDTO calc = pricingCalculateService.computePricing(p);
                    VehiclePriceEstimateDTO row = new VehiclePriceEstimateDTO();
                    row.setVehicleId(vehicle.getId());
                    row.setVehicleName(vehicle.getName());
                    row.setTotalAmount(calc.getTotalAmount());
                    rows.add(row);
                } catch (Exception skipped) {
                    // omit vehicles that fail weight or config
                }
            }
            rows.sort(Comparator.comparing(VehiclePriceEstimateDTO::getTotalAmount, Comparator.nullsLast(Double::compare)));
            response.setData(rows);
            response.setMessage("Incity vehicle estimates OK");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
        }
        return response;
    }
}

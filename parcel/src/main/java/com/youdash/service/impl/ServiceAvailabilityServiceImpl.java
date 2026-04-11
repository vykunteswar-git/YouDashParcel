package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.HubAvailabilityDTO;
import com.youdash.dto.NearestHubDTO;
import com.youdash.dto.OutstationDeliveryOptionDTO;
import com.youdash.dto.ServiceAvailabilityRequestDTO;
import com.youdash.dto.ServiceAvailabilityResponseDTO;
import com.youdash.dto.VehicleAvailabilityDTO;
import com.youdash.entity.HubEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.ZoneEntity;
import com.youdash.repository.HubRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.DistanceService;
import com.youdash.service.ServiceAvailabilityService;
import com.youdash.service.ZoneGeoService;
import com.youdash.util.GeoDistanceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServiceAvailabilityServiceImpl implements ServiceAvailabilityService {

    @Autowired
    private ZoneGeoService zoneGeoService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private DistanceService distanceService;

    @Override
    public ApiResponse<ServiceAvailabilityResponseDTO> check(ServiceAvailabilityRequestDTO dto) {
        ApiResponse<ServiceAvailabilityResponseDTO> response = new ApiResponse<>();
        try {
            if (dto == null) {
                throw new RuntimeException("Request body is required");
            }
            validateLatLng(dto.getPickupLat(), dto.getPickupLng(), "pickup");
            validateLatLng(dto.getDropLat(), dto.getDropLng(), "drop");
            validateWeightKg(dto.getWeightKg());

            double distanceKm = distanceService.calculateDistanceKm(
                    dto.getPickupLat(), dto.getPickupLng(),
                    dto.getDropLat(), dto.getDropLng());

            Optional<ZoneEntity> pz = zoneGeoService.findZoneContaining(dto.getPickupLat(), dto.getPickupLng());
            Optional<ZoneEntity> dz = zoneGeoService.findZoneContaining(dto.getDropLat(), dto.getDropLng());

            ServiceAvailabilityResponseDTO out = new ServiceAvailabilityResponseDTO();
            out.setStraightLineDistanceKm(round2(distanceKm));
            pz.ifPresent(z -> out.setPickupZoneId(z.getId()));
            dz.ifPresent(z -> out.setDropZoneId(z.getId()));

            boolean incity = pz.isPresent() && dz.isPresent() && pz.get().getId().equals(dz.get().getId());

            if (incity) {
                double weightKg = dto.getWeightKg();
                out.setServiceMode("INCITY");
                out.setServingZoneId(pz.get().getId());
                out.setVehicles(vehicleRepository.findByIsActiveTrue().stream()
                        .filter(v -> vehicleCanCarryWeight(v, weightKg))
                        .map(this::mapVehicle)
                        .collect(Collectors.toList()));
                out.setHubs(Collections.emptyList());
                out.setDeliveryOptions(Collections.emptyList());
                out.setNearestPickupHub(null);
                out.setNearestDropHub(null);
                out.setIsServiceable(!out.getVehicles().isEmpty());
            } else {
                out.setServiceMode("OUTSTATION");
                out.setServingZoneId(null);
                out.setVehicles(Collections.emptyList());
                List<HubEntity> activeHubs = hubRepository.findByIsActiveTrue();
                out.setHubs(activeHubs.stream()
                        .map(this::mapHub)
                        .collect(Collectors.toList()));
                out.setDeliveryOptions(outstationDeliveryOptionsWithLabels());
                out.setIsServiceable(!activeHubs.isEmpty());
                if (!activeHubs.isEmpty()) {
                    out.setNearestPickupHub(nearestHubDto(dto.getPickupLat(), dto.getPickupLng(), activeHubs));
                    out.setNearestDropHub(nearestHubDto(dto.getDropLat(), dto.getDropLng(), activeHubs));
                } else {
                    out.setNearestPickupHub(null);
                    out.setNearestDropHub(null);
                }
            }

            response.setData(out);
            response.setMessage("Service availability resolved");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(400);
        }
        return response;
    }

    private static List<OutstationDeliveryOptionDTO> outstationDeliveryOptionsWithLabels() {
        return List.of(
                new OutstationDeliveryOptionDTO(
                        "DOOR_TO_DOOR",
                        "Door to door",
                        "Pickup at sender address and delivery to receiver address."),
                new OutstationDeliveryOptionDTO(
                        "DOOR_TO_HUB",
                        "Door to hub",
                        "Pickup at sender address; receiver collects at destination hub."),
                new OutstationDeliveryOptionDTO(
                        "HUB_TO_DOOR",
                        "Hub to door",
                        "Sender drops at origin hub; delivery to receiver address."));
    }

    private NearestHubDTO nearestHubDto(double lat, double lng, List<HubEntity> hubs) {
        HubEntity best = hubs.stream()
                .min(Comparator.comparingDouble(h ->
                        GeoDistanceUtil.haversineKm(lat, lng, h.getLat(), h.getLng())))
                .orElseThrow();
        double dKm = GeoDistanceUtil.haversineKm(lat, lng, best.getLat(), best.getLng());
        NearestHubDTO n = new NearestHubDTO();
        n.setId(best.getId());
        n.setCity(best.getCity());
        n.setName(best.getName());
        n.setLat(best.getLat());
        n.setLng(best.getLng());
        n.setDistanceKm(round2(dKm));
        return n;
    }

    private static void validateLatLng(Double lat, Double lng, String label) {
        if (lat == null || lng == null) {
            throw new RuntimeException(label + " coordinates are required");
        }
        if (lat < -90 || lat > 90) {
            throw new RuntimeException(label + "Lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new RuntimeException(label + "Lng must be between -180 and 180");
        }
    }

    private static void validateWeightKg(Double weightKg) {
        if (weightKg == null) {
            throw new RuntimeException("weightKg is required");
        }
        if (weightKg <= 0) {
            throw new RuntimeException("weightKg must be greater than 0");
        }
        if (weightKg > 10000) {
            throw new RuntimeException("weightKg exceeds maximum allowed");
        }
    }

    /** Null maxWeight on a vehicle is treated as no configured limit (vehicle is eligible). */
    private static boolean vehicleCanCarryWeight(VehicleEntity v, double weightKg) {
        Double max = v.getMaxWeight();
        if (max == null) {
            return true;
        }
        return max >= weightKg;
    }

    private VehicleAvailabilityDTO mapVehicle(VehicleEntity v) {
        VehicleAvailabilityDTO d = new VehicleAvailabilityDTO();
        d.setId(v.getId());
        d.setName(v.getName());
        d.setMaxWeight(v.getMaxWeight());
        d.setPricePerKm(v.getPricePerKm());
        d.setBaseFare(v.getBaseFare());
        d.setMinimumKm(v.getMinimumKm());
        return d;
    }

    private HubAvailabilityDTO mapHub(HubEntity h) {
        HubAvailabilityDTO d = new HubAvailabilityDTO();
        d.setId(h.getId());
        d.setCity(h.getCity());
        d.setName(h.getName());
        d.setLat(h.getLat());
        d.setLng(h.getLng());
        return d;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

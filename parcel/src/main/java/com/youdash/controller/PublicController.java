package com.youdash.controller;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.DeliveryTypeOptionDTO;
import com.youdash.dto.PackageCategoryDTO;
import com.youdash.dto.VehicleDTO;
import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.entity.DeliveryTypeRateEntity;
import com.youdash.pricing.DeliveryScope;
import com.youdash.repository.DeliveryTypeRateRepository;
import com.youdash.repository.DeliveryTypeRepository;
import com.youdash.service.AdminService;
import com.youdash.service.DistanceService;
import com.youdash.service.ScopeResolverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/public")
public class PublicController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;

    @Autowired
    private DeliveryTypeRateRepository deliveryTypeRateRepository;

    @Autowired
    private ScopeResolverService scopeResolverService;

    @Autowired
    private DistanceService distanceService;

    @GetMapping("/vehicles")
    public ApiResponse<List<VehicleDTO>> getActiveVehicles() {
        return adminService.getActiveVehicles();
    }

    @GetMapping("/categories")
    public ApiResponse<List<PackageCategoryDTO>> getActiveCategories() {
        return adminService.getActiveCategories();
    }

    /**
     * Returns delivery type options for the user's trip. Backend resolves scope (IN_CITY/OUT_CITY).
     *
     * Provide either:
     * - distanceKm (old clients), OR
     * - pickupLat/pickupLng/deliveryLat/deliveryLng (preferred).
     */
    @GetMapping("/delivery-types")
    public ApiResponse<List<DeliveryTypeOptionDTO>> getDeliveryTypeOptions(
            @RequestParam(required = false) Double distanceKm,
            @RequestParam(required = false) Double pickupLat,
            @RequestParam(required = false) Double pickupLng,
            @RequestParam(required = false) Double deliveryLat,
            @RequestParam(required = false) Double deliveryLng
    ) {
        ApiResponse<List<DeliveryTypeOptionDTO>> response = new ApiResponse<>();
        try {
            Double resolvedDistance;
            boolean anyGeo = pickupLat != null || pickupLng != null || deliveryLat != null || deliveryLng != null;
            if (anyGeo) {
                if (pickupLat == null || pickupLng == null || deliveryLat == null || deliveryLng == null) {
                    throw new RuntimeException("pickupLat, pickupLng, deliveryLat, deliveryLng are required when using geo coordinates");
                }
                resolvedDistance = distanceService.calculateDistanceKm(pickupLat, pickupLng, deliveryLat, deliveryLng);
            } else {
                if (distanceKm == null || distanceKm <= 0) {
                    throw new RuntimeException("distanceKm must be > 0");
                }
                resolvedDistance = distanceKm;
            }

            DeliveryScope scope = scopeResolverService.resolveScope(resolvedDistance);

            List<DeliveryTypeEntity> activeTypes = deliveryTypeRepository.findByActiveTrueOrderByNameAsc();
            List<DeliveryTypeOptionDTO> options = activeTypes.stream()
                    .map(type -> {
                        DeliveryTypeRateEntity rate = deliveryTypeRateRepository
                                .findByDeliveryTypeAndScopeAndActiveTrue(type, scope)
                                .orElse(null);
                        if (rate == null) {
                            return null;
                        }
                        DeliveryTypeOptionDTO dto = new DeliveryTypeOptionDTO();
                        dto.setName(type.getName());
                        dto.setScope(scope.name());
                        dto.setFee(rate.getFee() == null ? 0.0 : rate.getFee().doubleValue());
                        dto.setDescription(rate.getDescription());
                        return dto;
                    })
                    .filter(o -> o != null)
                    .collect(Collectors.toList());

            response.setData(options);
            response.setMessage("Delivery types fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            response.setTotalCount(options.size());
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(400);
            response.setSuccess(false);
        }
        return response;
    }
}

package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.DeliveryPromiseDTO;
import com.youdash.dto.DeliveryTypeDetailsDTO;
import com.youdash.dto.DeliveryTypePricingDTO;
import com.youdash.dto.HubOptionDTO;
import com.youdash.dto.QuoteGstPricingDTO;
import com.youdash.dto.QuoteLegPricingDTO;
import com.youdash.dto.QuotePricingBreakdownDTO;
import com.youdash.dto.QuoteWeightPricingDTO;
import com.youdash.dto.SelectedHubsDTO;
import com.youdash.dto.QuoteRequestDTO;
import com.youdash.dto.QuoteResponseDTO;
import com.youdash.dto.VehicleOptionDTO;
import com.youdash.entity.HubRouteEntity;
import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.HubEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.ZoneEntity;
import com.youdash.model.OutstationDeliveryType;
import com.youdash.repository.AppConfigRepository;
import com.youdash.repository.HubRepository;
import com.youdash.repository.HubRouteRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.DeliveryPromiseService;
import com.youdash.service.DistanceService;
import com.youdash.service.PricingService;
import com.youdash.service.QuoteService;
import com.youdash.service.ZoneService;
import com.youdash.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class QuoteServiceImpl implements QuoteService {

    /** Only hubs within this distance (km) of pickup / drop are returned for outstation quote */
    private static final double HUB_SEARCH_RADIUS_KM = 50.0;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Autowired
    private DeliveryPromiseService deliveryPromiseService;

    @Override
    public ApiResponse<QuoteResponseDTO> quote(QuoteRequestDTO dto) {
        ApiResponse<QuoteResponseDTO> response = new ApiResponse<>();
        try {
            validateQuoteRequest(dto);
            double w = dto.getWeight();
            double pickupLat = dto.getPickupLat();
            double pickupLng = dto.getPickupLng();
            double dropLat = dto.getDropLat();
            double dropLng = dto.getDropLng();

            Optional<ZoneEntity> pz = zoneService.findZoneContaining(pickupLat, pickupLng);
            Optional<ZoneEntity> dz = zoneService.findZoneContaining(dropLat, dropLng);
            boolean sameZone = pz.isPresent() && dz.isPresent()
                    && pz.get().getId().equals(dz.get().getId());

            double distanceKm = distanceService.distanceKm(pickupLat, pickupLng, dropLat, dropLng);

            List<String> allDeliveryTypes = List.of(
                    OutstationDeliveryType.DOOR_TO_DOOR.name(),
                    OutstationDeliveryType.DOOR_TO_HUB.name(),
                    OutstationDeliveryType.HUB_TO_DOOR.name());

            if (sameZone) {
                AppConfigEntity cfg = appConfigRepository.findById(1L)
                        .orElseThrow(() -> new RuntimeException("Price config missing — ensure youdash_price_config row id=1 exists"));
                double gstPct = nz(cfg.getGstPercent());
                double platform = nz(cfg.getPlatformFee());

                // Exclude vehicles when parcel weight exceeds maxWeight
                List<VehicleEntity> vehicles = vehicleRepository.findByIsActiveTrue().stream()
                        .filter(v -> v.getMaxWeight() == null || v.getMaxWeight() >= w)
                        .collect(Collectors.toList());
                List<VehicleOptionDTO> options = new ArrayList<>();
                for (VehicleEntity v : vehicles) {
                    double sub = pricingService.incityVehicleTotal(distanceKm, w, v);
                    double gstAmt = sub * (gstPct / 100.0);
                    double grand = sub + gstAmt + platform;
                    options.add(VehicleOptionDTO.builder()
                            .vehicleId(v.getId())
                            .name(v.getName())
                            .imageUrl(v.getImageUrl())
                            .subtotal(round2(sub))
                            .estimatedTotal(round2(grand))
                            .maxWeight(v.getMaxWeight())
                            .perKm(v.getPricePerKm())
                            .gstPercent(gstPct)
                            .gstAmount(round2(gstAmt))
                            .platformFee(round2(platform))
                            .build());
                }
                QuoteResponseDTO data = QuoteResponseDTO.builder()
                        .serviceType("INCITY")
                        .distanceKm(round4(distanceKm))
                        .vehicleOptions(options)
                        .originHubs(List.of())
                        .destinationHubs(List.of())
                        .deliveryTypes(List.of())
                        .deliveryTypeDetails(List.of())
                        .manualRequest(false)
                        .serviceUnavailable(false)
                        .unavailableMessage(null)
                        .build();
                response.setData(data);
                response.setMessage("Quote ready");
            } else {
                List<HubEntity> hubs = hubRepository.findByIsActiveTrue();
                List<HubOptionDTO> originHubs = nearestHubs(pickupLat, pickupLng, hubs);
                List<HubOptionDTO> destHubs = nearestHubs(dropLat, dropLng, hubs);
                boolean noOrigin = originHubs.isEmpty();
                boolean noDest = destHubs.isEmpty();

                QuoteResponseDTO data;
                if (noOrigin && noDest) {
                    data = QuoteResponseDTO.builder()
                            .serviceType("OUTSTATION")
                            .distanceKm(round4(distanceKm))
                            .vehicleOptions(List.of())
                            .originHubs(List.of())
                            .destinationHubs(List.of())
                            .deliveryTypes(List.of())
                            .deliveryTypeDetails(List.of())
                            .manualRequest(false)
                            .serviceUnavailable(true)
                            .unavailableMessage("Service is not available at this location")
                            .build();
                    response.setMessage("Service is not available at this location");
                } else if (noOrigin || noDest) {
                    data = QuoteResponseDTO.builder()
                            .serviceType("OUTSTATION")
                            .distanceKm(round4(distanceKm))
                            .vehicleOptions(List.of())
                            .originHubs(originHubs)
                            .destinationHubs(destHubs)
                            .deliveryTypes(List.of())
                            .deliveryTypeDetails(List.of())
                            .manualRequest(true)
                            .serviceUnavailable(false)
                            .unavailableMessage(null)
                            .build();
                    response.setMessage("Quote ready");
                } else {
                    AppConfigEntity cfg = appConfigRepository.findById(1L)
                            .orElseThrow(() -> new RuntimeException("Price config missing — ensure youdash_price_config row id=1 exists"));
                    Long hubRouteId = resolveHubRouteId(originHubs, destHubs);
                    HubOptionDTO oHub = originHubs.get(0);
                    HubOptionDTO dHub = destHubs.get(0);
                    double pickupDist = distanceService.distanceKm(
                            pickupLat, pickupLng, oHub.getLat(), oHub.getLng());
                    double hubDist = distanceService.distanceKm(
                            oHub.getLat(), oHub.getLng(), dHub.getLat(), dHub.getLng());
                    double dropDist = distanceService.distanceKm(
                            dHub.getLat(), dHub.getLng(), dropLat, dropLng);
                    double routeRate = resolveRouteRate(oHub.getId(), dHub.getId(), cfg);
                    List<DeliveryTypeDetailsDTO> deliveryTypeDetails = buildDeliveryTypeDetails(
                            hubRouteId,
                            allDeliveryTypes,
                            w,
                            pickupDist,
                            hubDist,
                            dropDist,
                            routeRate,
                            cfg,
                            oHub.getId(),
                            dHub.getId());
                    data = QuoteResponseDTO.builder()
                            .serviceType("OUTSTATION")
                            .distanceKm(round4(distanceKm))
                            .vehicleOptions(List.of())
                            .originHubs(originHubs)
                            .destinationHubs(destHubs)
                            .deliveryTypes(allDeliveryTypes)
                            .deliveryTypeDetails(deliveryTypeDetails)
                            .manualRequest(false)
                            .serviceUnavailable(false)
                            .unavailableMessage(null)
                            .build();
                    response.setMessage("Quote ready");
                }
                response.setData(data);
            }
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    private void validateQuoteRequest(QuoteRequestDTO dto) {
        if (dto.getPickupLat() == null || dto.getPickupLng() == null
                || dto.getDropLat() == null || dto.getDropLng() == null) {
            throw new RuntimeException("pickup and drop coordinates are required");
        }
        if (dto.getWeight() == null || dto.getWeight() <= 0) {
            throw new RuntimeException("weight must be > 0");
        }
    }

    private Long resolveHubRouteId(List<HubOptionDTO> originHubs, List<HubOptionDTO> destHubs) {
        if (originHubs.isEmpty() || destHubs.isEmpty()) {
            return null;
        }
        return hubRouteRepository
                .findByOriginHubIdAndDestinationHubIdAndIsActiveTrue(
                        originHubs.get(0).getId(), destHubs.get(0).getId())
                .map(HubRouteEntity::getId)
                .orElse(null);
    }

    private List<DeliveryTypeDetailsDTO> buildDeliveryTypeDetails(
            Long hubRouteId,
            List<String> types,
            double weightKg,
            double pickupDist,
            double hubDist,
            double dropDist,
            double routeRate,
            AppConfigEntity cfg,
            Long selectedOriginHubId,
            Long selectedDestinationHubId) {
        List<DeliveryTypeDetailsDTO> out = new ArrayList<>();
        double pickupRate = nz(cfg.getPickupRatePerKm());
        double dropRate = nz(cfg.getDropRatePerKm());
        double perKg = nz(cfg.getPerKgRate());
        SelectedHubsDTO selectedHubs = new SelectedHubsDTO(selectedOriginHubId, selectedDestinationHubId);

        for (String type : types) {
            DeliveryTypeDetailsDTO row = new DeliveryTypeDetailsDTO();
            row.setType(type);
            row.setSelectedHubs(selectedHubs);
            switch (type) {
                case "DOOR_TO_DOOR" -> {
                    row.setTitle("Door to Door");
                    row.setDescription("Pickup and delivery at doorstep");
                    row.setHandoverMessage("We will pick up from your location");
                }
                case "DOOR_TO_HUB" -> {
                    row.setTitle("Door to Hub");
                    row.setDescription("We pick up, receiver collects from hub");
                    row.setHandoverMessage("We will deliver to selected hub");
                }
                case "HUB_TO_DOOR" -> {
                    row.setTitle("Hub to Door");
                    row.setDescription("Drop at hub, we deliver to doorstep");
                    row.setHandoverMessage("Drop your package at selected hub");
                }
                default -> {
                    row.setTitle(type);
                    row.setDescription("");
                    row.setHandoverMessage("");
                }
            }
            DeliveryPromiseDTO promise = deliveryPromiseService.getDeliveryPromise(hubRouteId, type);
            row.setDeliveryPromise(promise);
            applyPromiseCopy(row, promise, type);

            OutstationDeliveryType dtype = OutstationDeliveryType.valueOf(type);
            PricingService.OutstationBreakdown b = pricingService.outstationBreakdown(
                    pickupDist, hubDist, dropDist, routeRate, weightKg, dtype, cfg);
            row.setPricing(toDeliveryTypePricing(b, cfg, pickupRate, routeRate, dropRate, perKg, weightKg));
            out.add(row);
        }
        return out;
    }

    private static void applyPromiseCopy(
            DeliveryTypeDetailsDTO row, DeliveryPromiseDTO promise, String type) {
        if (promise == null) {
            return;
        }
        String cutoff = safe(promise.getCutoffInfo());
        String deliveredBy = safe(promise.getDeliveredBy());
        String slotMessage = safe(promise.getMessage());

        if (!cutoff.isEmpty() && !deliveredBy.isEmpty()) {
            row.setHandoverMessage(cutoff);
            row.setDescription(cutoff + " " + deliveredBy);
            return;
        }
        if (!cutoff.isEmpty()) {
            row.setHandoverMessage(cutoff);
            row.setDescription(cutoff);
            return;
        }
        if (!deliveredBy.isEmpty()) {
            row.setDescription(deliveredBy);
            return;
        }
        if (!slotMessage.isEmpty()) {
            row.setDescription(slotMessage);
            if ("HUB_TO_DOOR".equalsIgnoreCase(type)) {
                row.setHandoverMessage("Please hand over the parcel at the origin hub.");
            } else {
                row.setHandoverMessage("Rider pickup will be scheduled as per slot availability.");
            }
        }
    }

    private double resolveRouteRate(Long originHubId, Long destHubId, AppConfigEntity cfg) {
        return hubRouteRepository
                .findByOriginHubIdAndDestinationHubIdAndIsActiveTrue(originHubId, destHubId)
                .map(HubRouteEntity::getRatePerKm)
                .orElseGet(() -> nz(cfg.getDefaultRouteRatePerKm()));
    }

    private DeliveryTypePricingDTO toDeliveryTypePricing(
            PricingService.OutstationBreakdown b,
            AppConfigEntity cfg,
            double pickupRatePerKm,
            double hubRouteRatePerKm,
            double dropRatePerKm,
            double perKgRate,
            double weightKg) {

        QuoteLegPricingDTO pickup = QuoteLegPricingDTO.builder()
                .distanceKm(b.getPickupDistanceKm())
                .ratePerKm(round2(pickupRatePerKm))
                .cost(b.getPickupCost())
                .build();
        QuoteLegPricingDTO hubToHub = QuoteLegPricingDTO.builder()
                .distanceKm(b.getHubDistanceKm())
                .ratePerKm(round2(hubRouteRatePerKm))
                .cost(b.getHubCost())
                .build();
        QuoteLegPricingDTO lastMile = QuoteLegPricingDTO.builder()
                .distanceKm(b.getDropDistanceKm())
                .ratePerKm(round2(dropRatePerKm))
                .cost(b.getDropCost())
                .build();
        QuoteWeightPricingDTO weight = QuoteWeightPricingDTO.builder()
                .kg(weightKg)
                .ratePerKg(round2(perKgRate))
                .cost(b.getWeightCost())
                .build();

        QuoteGstPricingDTO gst = QuoteGstPricingDTO.builder()
                .percent(nz(cfg.getGstPercent()))
                .amount(b.getGstAmount())
                .build();

        QuotePricingBreakdownDTO breakdown = QuotePricingBreakdownDTO.builder()
                .pickup(pickup)
                .hubToHub(hubToHub)
                .lastMile(lastMile)
                .weight(weight)
                .build();

        return DeliveryTypePricingDTO.builder()
                .currency("INR")
                .breakdown(breakdown)
                .subtotal(b.getSubtotal())
                .platformFee(b.getPlatformFee())
                .gst(gst)
                .total(b.getTotal())
                .build();
    }

    private List<HubOptionDTO> nearestHubs(double lat, double lng, List<HubEntity> hubs) {
        return hubs.stream()
                .map(h -> {
                    double d = GeoUtils.haversineKm(lat, lng, h.getLat(), h.getLng());
                    return HubOptionDTO.builder()
                            .id(h.getId())
                            .name(h.getName())
                            .city(h.getCity())
                            .lat(h.getLat())
                            .lng(h.getLng())
                            .distanceKm(round4(d))
                            .build();
                })
                .filter(opt -> opt.getDistanceKm() != null && opt.getDistanceKm() <= HUB_SEARCH_RADIUS_KM)
                .sorted(Comparator.comparing(HubOptionDTO::getDistanceKm))
                .collect(Collectors.toList());
    }

    private static double nz(Double v) {
        return v != null ? v : 0.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.PricingCalculateRequestDTO;
import com.youdash.dto.PricingCalculateResponseDTO;
import com.youdash.entity.GlobalDeliveryConfigEntity;
import com.youdash.entity.HubEntity;
import com.youdash.entity.HubRouteEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.WeightPricingConfigEntity;
import com.youdash.model.FulfillmentType;
import com.youdash.repository.GlobalDeliveryConfigRepository;
import com.youdash.repository.HubRepository;
import com.youdash.repository.HubRouteRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.repository.WeightPricingConfigRepository;
import com.youdash.service.DistanceService;
import com.youdash.service.PricingCalculateService;
import com.youdash.service.ZoneGeoService;
import com.youdash.util.GeoDistanceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class PricingCalculateServiceImpl implements PricingCalculateService {

    @Autowired
    private ZoneGeoService zoneGeoService;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Autowired
    private GlobalDeliveryConfigRepository globalDeliveryConfigRepository;

    @Autowired
    private WeightPricingConfigRepository weightPricingConfigRepository;

    @Override
    public ApiResponse<PricingCalculateResponseDTO> calculate(PricingCalculateRequestDTO dto) {
        ApiResponse<PricingCalculateResponseDTO> response = new ApiResponse<>();
        try {
            response.setData(computePricing(dto));
            response.setMessage("Pricing calculated successfully");
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

    @Override
    public PricingCalculateResponseDTO computePricing(PricingCalculateRequestDTO dto) {
        if (dto == null) {
            throw new RuntimeException("Request body is required");
        }
        validateCoordinatePairs(dto);

        boolean hasPickup = dto.getPickupLat() != null && dto.getPickupLng() != null;
        boolean hasDrop = dto.getDropLat() != null && dto.getDropLng() != null;
        boolean hasAllFour = hasPickup && hasDrop;

        GlobalDeliveryConfigEntity global = globalDeliveryConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException("Global delivery config is not configured"));

        double weightKg = dto.getWeightKg() == null || dto.getWeightKg() < 0 ? 0.0 : dto.getWeightKg();
        double weightRate = weightPricingConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .map(WeightPricingConfigEntity::getRate)
                .filter(Objects::nonNull)
                .orElse(0.0);
        BigDecimal weightCharge = BigDecimal.valueOf(weightKg).multiply(BigDecimal.valueOf(weightRate))
                .setScale(2, RoundingMode.HALF_UP);

        if (hasAllFour) {
            validateLatLngRange(dto.getPickupLat(), dto.getPickupLng(), "pickup");
            validateLatLngRange(dto.getDropLat(), dto.getDropLng(), "drop");
            if (zoneGeoService.isSameIncityZone(
                    dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng())) {
                if (dto.getVehicleId() == null) {
                    throw new RuntimeException("vehicleId is required for INCITY pricing");
                }
                return computeIncityPricing(dto, global, weightCharge, weightKg);
            }
        }

        String deliveryOpt = normalizeOutstationDeliveryOption(dto.getDeliveryOption());
        if (deliveryOpt == null) {
            throw new RuntimeException("deliveryOption is required for outstation pricing");
        }
        validateCoordinatesForOutstationOption(dto, deliveryOpt, hasPickup, hasDrop);

        return computeOutstationPricing(dto, global, weightCharge, deliveryOpt, hasPickup, hasDrop);
    }

    private PricingCalculateResponseDTO computeIncityPricing(
            PricingCalculateRequestDTO dto,
            GlobalDeliveryConfigEntity global,
            BigDecimal weightCharge,
            double weightKg) {
        double distKm = distanceService.calculateDistanceKm(
                dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng());

        VehicleEntity vehicle = vehicleRepository.findById(dto.getVehicleId())
                .orElseThrow(() -> new RuntimeException("Vehicle not found: " + dto.getVehicleId()));
        if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
            throw new RuntimeException("Vehicle is inactive");
        }
        if (weightKg > 0 && vehicle.getMaxWeight() != null && weightKg > vehicle.getMaxWeight()) {
            throw new RuntimeException("Vehicle cannot carry this weight");
        }

        BigDecimal distanceBd = BigDecimal.valueOf(distKm);
        BigDecimal minKm = BigDecimal.valueOf(Optional.ofNullable(vehicle.getMinimumKm()).orElse(0.0));
        BigDecimal chargeableKm = distanceBd.subtract(minKm);
        if (chargeableKm.signum() < 0) {
            chargeableKm = BigDecimal.ZERO;
        }
        BigDecimal vehiclePart = nz(BigDecimal.valueOf(Optional.ofNullable(vehicle.getBaseFare()).orElse(0.0)))
                .add(chargeableKm.multiply(BigDecimal.valueOf(Optional.ofNullable(vehicle.getPricePerKm()).orElse(0.0))))
                .setScale(2, RoundingMode.HALF_UP);

        PricingCalculateResponseDTO out = new PricingCalculateResponseDTO();
        out.setStraightLineDistanceKm(round2(distKm));
        out.getBreakdownLines().add("Pickup–drop route distance (km): " + round2(distKm));
        out.setServiceMode("INCITY");
        out.setDeliveryOption(null);
        out.setFirstMileCost(null);
        out.setHubRouteCost(null);
        out.setLastMileCost(null);
        out.setOriginHubId(null);
        out.setDestinationHubId(null);
        out.setFirstMileDistanceKm(null);
        out.setLastMileDistanceKm(null);
        out.setFirstMileRatePerKm(null);
        out.setLastMileRatePerKm(null);
        out.setVehicleCost(vehiclePart.doubleValue());
        out.setWeightCost(weightCharge.doubleValue());
        out.setHubTransportCost(null);
        out.setHubPathIds(new ArrayList<>());
        out.setRoute(new ArrayList<>());
        out.getBreakdownLines().add("Incity: vehicle base + chargeableKm × vehicle.pricePerKm (distance = route km)");
        out.getBreakdownLines().add("Weight: weightKg × global weight rate");

        BigDecimal zonePart = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal firstMile = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal interHub = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lastMile = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        out.setVehicleComponent(vehiclePart.doubleValue());
        out.setZoneComponent(zonePart.doubleValue());
        out.setFirstMileComponent(firstMile.doubleValue());
        out.setInterHubComponent(interHub.doubleValue());
        out.setLastMileComponent(lastMile.doubleValue());

        applyFeesAndTotal(out, global, vehiclePart, zonePart, firstMile, interHub, lastMile, weightCharge);
        return out;
    }

    private PricingCalculateResponseDTO computeOutstationPricing(
            PricingCalculateRequestDTO dto,
            GlobalDeliveryConfigEntity global,
            BigDecimal weightCharge,
            String deliveryOpt,
            boolean hasPickup,
            boolean hasDrop) {

        List<HubEntity> hubs = hubRepository.findByIsActiveTrue();
        if (hubs.isEmpty()) {
            throw new RuntimeException("No active hubs configured for outstation pricing");
        }

        HubEntity startHub = resolveStartHub(dto, hubs, deliveryOpt, hasPickup);
        HubEntity endHub = resolveEndHub(dto, hubs, deliveryOpt, hasDrop);

        List<HubRouteEntity> edges = hubRouteRepository.findByIsActiveTrue();
        List<HubRouteEntity> path = shortestPath(startHub.getId(), endHub.getId(), edges);
        if (path == null) {
            throw new RuntimeException("No active hub route connects hubs " + startHub.getId() + " -> " + endHub.getId());
        }

        double dFirst = hasPickup
                ? distanceService.calculateDistanceKm(
                        dto.getPickupLat(), dto.getPickupLng(), startHub.getLat(), startHub.getLng())
                : 0.0;
        double dLast = hasDrop
                ? distanceService.calculateDistanceKm(
                        endHub.getLat(), endHub.getLng(), dto.getDropLat(), dto.getDropLng())
                : 0.0;

        double firstRate = resolveMileRatePerKm(global.getFirstMileRatePerKm(), global.getIncityExtraRatePerKm());
        double lastRate = resolveMileRatePerKm(global.getLastMileRatePerKm(), global.getIncityExtraRatePerKm());

        BigDecimal firstMile = BigDecimal.valueOf(dFirst).multiply(BigDecimal.valueOf(firstRate)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal lastMile = BigDecimal.valueOf(dLast).multiply(BigDecimal.valueOf(lastRate)).setScale(2, RoundingMode.HALF_UP);

        BigDecimal interHub = BigDecimal.ZERO;
        for (HubRouteEntity leg : path) {
            interHub = interHub.add(routeLegCost(leg));
        }
        interHub = interHub.setScale(2, RoundingMode.HALF_UP);

        BigDecimal vehiclePart = BigDecimal.ZERO;
        BigDecimal zonePart = BigDecimal.ZERO;

        double refKm = resolveReferenceDistanceKm(dto, deliveryOpt, startHub, endHub, hasPickup, hasDrop);
        PricingCalculateResponseDTO out = new PricingCalculateResponseDTO();
        out.setStraightLineDistanceKm(refKm);
        out.getBreakdownLines().add("Reference route distance (km): " + round2(refKm));

        switch (deliveryOpt) {
            case FulfillmentType.DOOR_TO_HUB -> {
                lastMile = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            case FulfillmentType.HUB_TO_DOOR -> {
                firstMile = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            }
            case FulfillmentType.DOOR_TO_DOOR -> {
                // keep computed first/last mile costs
            }
            default -> throw new RuntimeException("Invalid delivery option");
        }

        out.setServiceMode("OUTSTATION");
        out.setDeliveryOption(deliveryOpt);
        out.setOriginHubId(startHub.getId());
        out.setDestinationHubId(endHub.getId());
        out.setFirstMileDistanceKm(round2(dFirst));
        out.setLastMileDistanceKm(round2(dLast));
        out.setFirstMileRatePerKm(firstRate);
        out.setLastMileRatePerKm(lastRate);
        out.setFirstMileCost(firstMile.doubleValue());
        out.setHubRouteCost(interHub.doubleValue());
        out.setLastMileCost(lastMile.doubleValue());
        out.setHubTransportCost(interHub.doubleValue());
        out.setVehicleCost(null);
        out.setWeightCost(weightCharge.doubleValue());

        List<Long> hubIds = new ArrayList<>();
        hubIds.add(startHub.getId());
        for (HubRouteEntity leg : path) {
            hubIds.add(leg.getDestinationHub().getId());
        }
        out.setHubPathIds(hubIds);
        out.setRoute(resolveRouteLabels(hubIds));

        appendOutstationDetailedBreakdown(out, startHub, endHub, dFirst, dLast, firstRate, lastRate,
                firstMile, interHub, lastMile, weightCharge, deliveryOpt);

        out.setVehicleComponent(vehiclePart.doubleValue());
        out.setZoneComponent(zonePart.doubleValue());
        out.setFirstMileComponent(firstMile.doubleValue());
        out.setInterHubComponent(interHub.doubleValue());
        out.setLastMileComponent(lastMile.doubleValue());

        applyFeesAndTotal(out, global, vehiclePart, zonePart, firstMile, interHub, lastMile, weightCharge);
        return out;
    }

    private double resolveReferenceDistanceKm(
            PricingCalculateRequestDTO dto,
            String deliveryOpt,
            HubEntity startHub,
            HubEntity endHub,
            boolean hasPickup,
            boolean hasDrop) {
        return switch (deliveryOpt) {
            case FulfillmentType.DOOR_TO_DOOR -> distanceService.calculateDistanceKm(
                    dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng());
            case FulfillmentType.DOOR_TO_HUB -> {
                if (hasDrop) {
                    yield distanceService.calculateDistanceKm(
                            dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng());
                }
                yield distanceService.calculateDistanceKm(
                        dto.getPickupLat(), dto.getPickupLng(), endHub.getLat(), endHub.getLng());
            }
            case FulfillmentType.HUB_TO_DOOR -> {
                if (hasPickup) {
                    yield distanceService.calculateDistanceKm(
                            dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng());
                }
                yield distanceService.calculateDistanceKm(
                        startHub.getLat(), startHub.getLng(), dto.getDropLat(), dto.getDropLng());
            }
            default -> 0.0;
        };
    }

    private void appendOutstationDetailedBreakdown(
            PricingCalculateResponseDTO out,
            HubEntity startHub,
            HubEntity endHub,
            double dFirst,
            double dLast,
            double firstRate,
            double lastRate,
            BigDecimal firstMile,
            BigDecimal interHub,
            BigDecimal lastMile,
            BigDecimal weightCharge,
            String deliveryOpt) {

        String startLabel = hubLabel(startHub);
        String endLabel = hubLabel(endHub);

        out.getBreakdownLines().add("--- Outstation pricing ---");
        out.getBreakdownLines().add("Origin hub (first-mile side): " + startLabel + " [id=" + startHub.getId() + "]");
        out.getBreakdownLines().add("Destination hub (last-mile side): " + endLabel + " [id=" + endHub.getId() + "]");

        switch (deliveryOpt) {
            case FulfillmentType.DOOR_TO_DOOR, FulfillmentType.DOOR_TO_HUB -> {
                out.getBreakdownLines().add(String.format(
                        "Pickup → origin hub: %.2f km × ₹%.2f/km = ₹%.2f",
                        dFirst, firstRate, firstMile.doubleValue()));
            }
            case FulfillmentType.HUB_TO_DOOR -> {
                out.getBreakdownLines().add("Pickup → origin hub: not charged for this option (₹0.00)");
            }
            default -> {
            }
        }

        out.getBreakdownLines().add(String.format(
                "Hub line haul (%s → %s): ₹%.2f",
                startLabel, endLabel, interHub.doubleValue()));

        switch (deliveryOpt) {
            case FulfillmentType.DOOR_TO_DOOR, FulfillmentType.HUB_TO_DOOR -> {
                out.getBreakdownLines().add(String.format(
                        "Destination hub → drop: %.2f km × ₹%.2f/km = ₹%.2f",
                        dLast, lastRate, lastMile.doubleValue()));
            }
            case FulfillmentType.DOOR_TO_HUB -> {
                out.getBreakdownLines().add("Destination hub → drop: not charged for this option (₹0.00)");
            }
            default -> {
            }
        }

        out.getBreakdownLines().add(String.format("Weight surcharge: ₹%.2f", weightCharge.doubleValue()));
        out.getBreakdownLines().add(String.format(
                "Sum of line items (before min GST platform): ₹%.2f",
                firstMile.add(interHub).add(lastMile).add(weightCharge).setScale(2, RoundingMode.HALF_UP).doubleValue()));
    }

    private static String hubLabel(HubEntity h) {
        if (h.getCity() != null && !h.getCity().isBlank()) {
            return h.getCity().trim();
        }
        if (h.getName() != null && !h.getName().isBlank()) {
            return h.getName().trim();
        }
        return "Hub-" + h.getId();
    }

    private void applyFeesAndTotal(
            PricingCalculateResponseDTO out,
            GlobalDeliveryConfigEntity global,
            BigDecimal vehiclePart,
            BigDecimal zonePart,
            BigDecimal firstMile,
            BigDecimal interHub,
            BigDecimal lastMile,
            BigDecimal weightCharge) {

        BigDecimal preMin = vehiclePart.add(zonePart).add(firstMile).add(interHub).add(lastMile).add(weightCharge).setScale(2, RoundingMode.HALF_UP);
        out.setSubTotalBeforeMin(preMin.doubleValue());
        out.setWeightCharge(weightCharge.doubleValue());

        BigDecimal minCharge = BigDecimal.valueOf(Optional.ofNullable(global.getMinimumCharge()).orElse(0.0));
        BigDecimal afterMin = preMin.max(minCharge).setScale(2, RoundingMode.HALF_UP);
        out.setMinimumChargeApplied(afterMin.subtract(preMin).max(BigDecimal.ZERO).doubleValue());
        out.setSubTotalAfterMin(afterMin.doubleValue());

        BigDecimal platform = BigDecimal.valueOf(Optional.ofNullable(global.getPlatformFee()).orElse(0.0))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal gstPercentBd = BigDecimal.valueOf(Optional.ofNullable(global.getGstPercent()).orElse(0.0));

        BigDecimal gstAmount = afterMin.multiply(gstPercentBd).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = afterMin.add(platform).add(gstAmount).setScale(2, RoundingMode.HALF_UP);

        out.setPlatformFee(platform.doubleValue());
        out.setGstPercent(gstPercentBd.doubleValue());
        out.setGstAmount(gstAmount.doubleValue());
        out.setTotalAmount(total.doubleValue());

        out.getBreakdownLines().add("Subtotal after minimum charge: " + afterMin.doubleValue());
        out.getBreakdownLines().add("Platform fee: " + platform.doubleValue());
        out.getBreakdownLines().add("GST (" + gstPercentBd.stripTrailingZeros().toPlainString() + "%): " + gstAmount.doubleValue());
        out.getBreakdownLines().add("Total: " + total.doubleValue());
    }

    private static void validateCoordinatePairs(PricingCalculateRequestDTO dto) {
        boolean pLat = dto.getPickupLat() != null;
        boolean pLng = dto.getPickupLng() != null;
        if (pLat != pLng) {
            throw new RuntimeException("pickupLat and pickupLng must both be sent or both omitted");
        }
        boolean dLat = dto.getDropLat() != null;
        boolean dLng = dto.getDropLng() != null;
        if (dLat != dLng) {
            throw new RuntimeException("dropLat and dropLng must both be sent or both omitted");
        }
    }

    private static void validateCoordinatesForOutstationOption(
            PricingCalculateRequestDTO dto,
            String opt,
            boolean hasPickup,
            boolean hasDrop) {
        switch (opt) {
            case FulfillmentType.DOOR_TO_DOOR -> {
                if (!hasPickup || !hasDrop) {
                    throw new RuntimeException("pickup and drop coordinates are required for DOOR_TO_DOOR");
                }
            }
            case FulfillmentType.DOOR_TO_HUB -> {
                if (!hasPickup) {
                    throw new RuntimeException("pickupLat and pickupLng are required for DOOR_TO_HUB");
                }
                if (!hasDrop && dto.getDestinationHubId() == null) {
                    throw new RuntimeException("destinationHubId is required for DOOR_TO_HUB when drop coordinates are omitted");
                }
            }
            case FulfillmentType.HUB_TO_DOOR -> {
                if (!hasDrop) {
                    throw new RuntimeException("dropLat and dropLng are required for HUB_TO_DOOR");
                }
                if (!hasPickup && dto.getSourceHubId() == null) {
                    throw new RuntimeException("sourceHubId is required for HUB_TO_DOOR when pickup coordinates are omitted");
                }
            }
            default -> throw new RuntimeException("Invalid delivery option");
        }
    }

    private HubEntity resolveStartHub(
            PricingCalculateRequestDTO dto,
            List<HubEntity> hubs,
            String deliveryOpt,
            boolean hasPickup) {
        if (FulfillmentType.HUB_TO_DOOR.equals(deliveryOpt)) {
            if (dto.getSourceHubId() != null) {
                return requireActiveHub(dto.getSourceHubId(), "sourceHubId");
            }
            if (hasPickup) {
                return nearestHub(dto.getPickupLat(), dto.getPickupLng(), hubs);
            }
            throw new RuntimeException("sourceHubId is required for HUB_TO_DOOR when pickup coordinates are omitted");
        }
        if (dto.getSourceHubId() != null) {
            return requireActiveHub(dto.getSourceHubId(), "sourceHubId");
        }
        if (!hasPickup) {
            throw new RuntimeException("pickupLat and pickupLng are required for this delivery option when sourceHubId is omitted");
        }
        return nearestHub(dto.getPickupLat(), dto.getPickupLng(), hubs);
    }

    private HubEntity resolveEndHub(
            PricingCalculateRequestDTO dto,
            List<HubEntity> hubs,
            String deliveryOpt,
            boolean hasDrop) {
        if (FulfillmentType.DOOR_TO_HUB.equals(deliveryOpt)) {
            if (dto.getDestinationHubId() != null) {
                return requireActiveHub(dto.getDestinationHubId(), "destinationHubId");
            }
            if (hasDrop) {
                return nearestHub(dto.getDropLat(), dto.getDropLng(), hubs);
            }
            throw new RuntimeException("destinationHubId is required for DOOR_TO_HUB when drop coordinates are omitted");
        }
        if (dto.getDestinationHubId() != null) {
            return requireActiveHub(dto.getDestinationHubId(), "destinationHubId");
        }
        if (!hasDrop) {
            throw new RuntimeException("dropLat and dropLng are required for this delivery option when destinationHubId is omitted");
        }
        return nearestHub(dto.getDropLat(), dto.getDropLng(), hubs);
    }

    private HubEntity requireActiveHub(Long id, String label) {
        if (id == null) {
            throw new RuntimeException(label + " is required");
        }
        return hubRepository.findById(id)
                .filter(h -> Boolean.TRUE.equals(h.getIsActive()))
                .orElseThrow(() -> new RuntimeException("Invalid or inactive " + label + ": " + id));
    }

    private static String normalizeOutstationDeliveryOption(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        if (FulfillmentType.HUB_TO_HUB.equals(u)) {
            throw new RuntimeException("HUB_TO_HUB is no longer supported; use DOOR_TO_DOOR, DOOR_TO_HUB, or HUB_TO_DOOR");
        }
        if (FulfillmentType.DOOR_TO_DOOR.equals(u)
                || FulfillmentType.DOOR_TO_HUB.equals(u)
                || FulfillmentType.HUB_TO_DOOR.equals(u)) {
            return u;
        }
        throw new RuntimeException("deliveryOption must be DOOR_TO_DOOR, DOOR_TO_HUB, or HUB_TO_DOOR");
    }

    private static double resolveMileRatePerKm(Double dedicated, Double legacyFallback) {
        if (dedicated != null && dedicated >= 0) {
            return dedicated;
        }
        return Optional.ofNullable(legacyFallback).orElse(0.0);
    }

    private List<String> resolveRouteLabels(List<Long> hubIds) {
        List<String> labels = new ArrayList<>();
        for (Long hid : hubIds) {
            hubRepository.findById(hid).ifPresent(h -> {
                String city = h.getCity();
                if (city != null && !city.isBlank()) {
                    labels.add(city.trim());
                } else if (h.getName() != null && !h.getName().isBlank()) {
                    labels.add(h.getName().trim());
                } else {
                    labels.add("Hub-" + hid);
                }
            });
        }
        return labels;
    }

    private static HubEntity nearestHub(double lat, double lng, List<HubEntity> hubs) {
        return hubs.stream()
                .min(Comparator.comparingDouble(h ->
                        GeoDistanceUtil.haversineKm(lat, lng, h.getLat(), h.getLng())))
                .orElseThrow();
    }

    private static List<HubRouteEntity> shortestPath(Long startHubId, Long endHubId, List<HubRouteEntity> edges) {
        if (startHubId.equals(endHubId)) {
            return List.of();
        }
        Map<Long, List<HubRouteEntity>> adj = new HashMap<>();
        for (HubRouteEntity e : edges) {
            Long s = e.getSourceHub().getId();
            adj.computeIfAbsent(s, k -> new ArrayList<>()).add(e);
        }
        Deque<Long> q = new ArrayDeque<>();
        q.add(startHubId);
        Set<Long> seen = new HashSet<>();
        seen.add(startHubId);
        Map<Long, HubRouteEntity> incoming = new HashMap<>();
        while (!q.isEmpty()) {
            Long u = q.poll();
            if (u.equals(endHubId)) {
                break;
            }
            for (HubRouteEntity e : adj.getOrDefault(u, List.of())) {
                Long v = e.getDestinationHub().getId();
                if (seen.contains(v)) {
                    continue;
                }
                seen.add(v);
                incoming.put(v, e);
                q.add(v);
            }
        }
        if (!seen.contains(endHubId)) {
            return null;
        }
        LinkedList<HubRouteEntity> path = new LinkedList<>();
        Long cur = endHubId;
        while (!cur.equals(startHubId)) {
            HubRouteEntity e = incoming.get(cur);
            if (e == null) {
                return null;
            }
            path.addFirst(e);
            cur = e.getSourceHub().getId();
        }
        return path;
    }

    private static BigDecimal routeLegCost(HubRouteEntity r) {
        double legKm = GeoDistanceUtil.haversineKm(
                r.getSourceHub().getLat(), r.getSourceHub().getLng(),
                r.getDestinationHub().getLat(), r.getDestinationHub().getLng());
        if (r.getFixedPrice() != null && r.getFixedPrice() > 0) {
            return BigDecimal.valueOf(r.getFixedPrice());
        }
        double ppk = r.getPricePerKm() == null ? 0.0 : r.getPricePerKm();
        return BigDecimal.valueOf(legKm).multiply(BigDecimal.valueOf(ppk));
    }

    private static void validateLatLngRange(Double lat, Double lng, String label) {
        if (lat < -90 || lat > 90) {
            throw new RuntimeException(label + "Lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new RuntimeException(label + "Lng must be between -180 and 180");
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.entity.*;
import com.youdash.model.*;
import com.youdash.repository.*;
import com.youdash.notification.NotificationType;
import com.youdash.service.DistanceService;
import com.youdash.service.NotificationService;
import com.youdash.service.OrderService;
import com.youdash.service.PricingService;
import com.youdash.service.ZoneService;
import com.youdash.util.GeoUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private AppConfigRepository appConfigRepository;

    @Autowired
    private ZoneService zoneService;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private HubRepository hubRepository;

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PackageCategoryRepository packageCategoryRepository;

    @Autowired
    private ManualOrderRequestRepository manualOrderRequestRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private NotificationService notificationService;

    @Override
    public ApiResponse<FinalPriceResponseDTO> calculateFinal(FinalPriceRequestDTO dto) {
        ApiResponse<FinalPriceResponseDTO> response = new ApiResponse<>();
        try {
            validateCoordsWeight(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng(), dto.getWeight());
            if (dto.getOriginHubId() == null || dto.getDestinationHubId() == null) {
                throw new RuntimeException("originHubId and destinationHubId are required");
            }
            OutstationDeliveryType dtype = parseOutstationDelivery(dto.getDeliveryType());
            HubEntity origin = hubRepository.findById(dto.getOriginHubId())
                    .filter(h -> Boolean.TRUE.equals(h.getIsActive()))
                    .orElseThrow(() -> new RuntimeException("Origin hub not found or inactive"));
            HubEntity dest = hubRepository.findById(dto.getDestinationHubId())
                    .filter(h -> Boolean.TRUE.equals(h.getIsActive()))
                    .orElseThrow(() -> new RuntimeException("Destination hub not found or inactive"));

            AppConfigEntity cfg = requireConfig();
            double routeRate = resolveRouteRate(dto.getOriginHubId(), dto.getDestinationHubId(), cfg);

            double pickupDist = distanceService.distanceKm(
                    dto.getPickupLat(), dto.getPickupLng(), origin.getLat(), origin.getLng());
            double hubDist = distanceService.distanceKm(
                    origin.getLat(), origin.getLng(), dest.getLat(), dest.getLng());
            double dropDist = distanceService.distanceKm(
                    dest.getLat(), dest.getLng(), dto.getDropLat(), dto.getDropLng());

            PricingService.OutstationBreakdown b = pricingService.outstationBreakdown(
                    pickupDist, hubDist, dropDist, routeRate, dto.getWeight(), dtype, cfg);

            FinalPriceResponseDTO data = FinalPriceResponseDTO.builder()
                    .pickupDistanceKm(b.getPickupDistanceKm())
                    .hubDistanceKm(b.getHubDistanceKm())
                    .dropDistanceKm(b.getDropDistanceKm())
                    .pickupCost(b.getPickupCost())
                    .hubCost(b.getHubCost())
                    .dropCost(b.getDropCost())
                    .weightCost(b.getWeightCost())
                    .subtotal(b.getSubtotal())
                    .gstAmount(b.getGstAmount())
                    .platformFee(b.getPlatformFee())
                    .total(b.getTotal())
                    .build();
            response.setData(data);
            response.setMessage("Final price calculated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> createOrder(Long userId, CreateOrderRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            validateCoordsWeight(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng(), dto.getWeight());
            validateContactFields(dto);
            if (dto.getCategoryId() == null) {
                throw new RuntimeException("categoryId is required");
            }
            PackageCategoryEntity category = packageCategoryRepository.findById(dto.getCategoryId())
                    .filter(c -> Boolean.TRUE.equals(c.getIsActive()))
                    .orElseThrow(() -> new RuntimeException("Package category not found or inactive"));
            String resolvedDeliveryType = resolveDeliveryTypeForOrder(category, dto);
            if (dto.getPaymentType() == null || dto.getPaymentType().isBlank()) {
                throw new RuntimeException("paymentType is required (COD or ONLINE)");
            }
            PaymentType paymentType = PaymentType.valueOf(dto.getPaymentType().trim().toUpperCase());

            Optional<com.youdash.entity.ZoneEntity> pz = zoneService.findZoneContaining(dto.getPickupLat(), dto.getPickupLng());
            Optional<com.youdash.entity.ZoneEntity> dz = zoneService.findZoneContaining(dto.getDropLat(), dto.getDropLng());
            boolean sameZone = pz.isPresent() && dz.isPresent()
                    && pz.get().getId().equals(dz.get().getId());

            OrderEntity order = new OrderEntity();
            order.setUserId(userId);
            order.setPackageCategoryId(category.getId());
            order.setSenderName(trimToNull(dto.getSenderName()));
            order.setSenderPhone(trimToNull(dto.getSenderPhone()));
            order.setReceiverName(trimToNull(dto.getReceiverName()));
            order.setReceiverPhone(trimToNull(dto.getReceiverPhone()));
            order.setPickupLat(dto.getPickupLat());
            order.setPickupLng(dto.getPickupLng());
            order.setDropLat(dto.getDropLat());
            order.setDropLng(dto.getDropLng());
            order.setWeight(dto.getWeight());
            order.setPaymentType(paymentType);
            order.setDeliveryType(resolvedDeliveryType);
            if (dto.getVehiclePricePerKm() != null) {
                order.setVehiclePricePerKm(round4(dto.getVehiclePricePerKm()));
            }

            assertPricingAllOrNothing(dto);
            boolean clientPricing = hasFullClientPricing(dto);

            if (sameZone) {
                if (dto.getVehicleId() == null) {
                    throw new RuntimeException("INCITY order requires vehicleId");
                }
                VehicleEntity vehicle = vehicleRepository.findById(dto.getVehicleId())
                        .filter(v -> Boolean.TRUE.equals(v.getIsActive()))
                        .orElseThrow(() -> new RuntimeException("Vehicle not found or inactive"));
                if (vehicle.getMaxWeight() != null && dto.getWeight() > vehicle.getMaxWeight()) {
                    throw new RuntimeException("Weight exceeds vehicle maxWeight");
                }
                double dist = distanceService.distanceKm(
                        dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng());

                order.setServiceMode(ServiceMode.INCITY);
                order.setVehicleId(vehicle.getId());
                order.setOriginHubId(null);
                order.setDestinationHubId(null);
                order.setPickupDistanceKm(round4(dist));
                order.setHubDistanceKm(null);
                order.setDropDistanceKm(null);
                if (order.getVehiclePricePerKm() == null && vehicle.getPricePerKm() != null) {
                    order.setVehiclePricePerKm(round4(vehicle.getPricePerKm()));
                }

                if (clientPricing) {
                    applyClientPricing(order, dto);
                } else {
                    AppConfigEntity cfg = requireConfig();
                    double sub = pricingService.incityVehicleTotal(dist, dto.getWeight(), vehicle);
                    double gst = sub * (nz(cfg.getGstPercent()) / 100.0);
                    double platform = nz(cfg.getPlatformFee());
                    double baseTotal = round2(sub + gst + platform);
                    double couponDisc = resolveCouponAmount(dto.getCouponAmount(), baseTotal);
                    order.setSubtotal(round2(sub));
                    order.setGstAmount(round2(gst));
                    order.setPlatformFee(round2(platform));
                    order.setCouponAmount(round2(couponDisc));
                    order.setTotalAmount(round2(baseTotal - couponDisc));
                }

                Optional<Long> riderOpt = pickNearestAvailableRider(dto.getPickupLat(), dto.getPickupLng());
                if (riderOpt.isPresent()) {
                    order.setRiderId(riderOpt.get());
                    order.setStatus(OrderStatus.ASSIGNED);
                } else {
                    order.setStatus(OrderStatus.PENDING);
                }
            } else {
                if (dto.getOriginHubId() == null || dto.getDestinationHubId() == null) {
                    throw new RuntimeException("OUTSTATION order requires originHubId and destinationHubId");
                }
                OutstationDeliveryType dtype = parseOutstationDelivery(resolvedDeliveryType);
                HubEntity origin = hubRepository.findById(dto.getOriginHubId())
                        .filter(h -> Boolean.TRUE.equals(h.getIsActive()))
                        .orElseThrow(() -> new RuntimeException("Origin hub not found or inactive"));
                HubEntity dest = hubRepository.findById(dto.getDestinationHubId())
                        .filter(h -> Boolean.TRUE.equals(h.getIsActive()))
                        .orElseThrow(() -> new RuntimeException("Destination hub not found or inactive"));

                double pickupDist = distanceService.distanceKm(
                        dto.getPickupLat(), dto.getPickupLng(), origin.getLat(), origin.getLng());
                double hubDist = distanceService.distanceKm(
                        origin.getLat(), origin.getLng(), dest.getLat(), dest.getLng());
                double dropDist = distanceService.distanceKm(
                        dest.getLat(), dest.getLng(), dto.getDropLat(), dto.getDropLng());
                LegKm legs = outstationLegKm(pickupDist, hubDist, dropDist, dtype);

                order.setServiceMode(ServiceMode.OUTSTATION);
                order.setVehicleId(null);
                order.setOriginHubId(origin.getId());
                order.setDestinationHubId(dest.getId());
                order.setPickupDistanceKm(legs.pickupKm());
                order.setHubDistanceKm(legs.hubKm());
                order.setDropDistanceKm(legs.dropKm());

                if (clientPricing) {
                    applyClientPricing(order, dto);
                } else {
                    AppConfigEntity cfg = requireConfig();
                    double routeRate = resolveRouteRate(dto.getOriginHubId(), dto.getDestinationHubId(), cfg);
                    PricingService.OutstationBreakdown b = pricingService.outstationBreakdown(
                            pickupDist, hubDist, dropDist, routeRate, dto.getWeight(), dtype, cfg);
                    double couponDiscOs = resolveCouponAmount(dto.getCouponAmount(), b.getTotal());
                    order.setSubtotal(b.getSubtotal());
                    order.setGstAmount(b.getGstAmount());
                    order.setPlatformFee(b.getPlatformFee());
                    order.setCouponAmount(round2(couponDiscOs));
                    order.setTotalAmount(round2(b.getTotal() - couponDiscOs));
                }
                order.setStatus(OrderStatus.PENDING_ASSIGNMENT);
            }

            OrderEntity saved = orderRepository.save(order);
            notifyAfterOrderCreated(saved);
            response.setData(toOrderDto(saved));
            response.setMessage("Order created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> getOrder(Long orderId, Long tokenUserId, boolean admin) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!admin && !Objects.equals(o.getUserId(), tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
            response.setData(toOrderDto(o));
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<OrderResponseDTO>> listUserOrders(Long userId, Long tokenUserId, boolean admin) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            if (!admin && !Objects.equals(userId, tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
            List<OrderResponseDTO> list = orderRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .map(this::toOrderDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<ManualOrderRequestResponseDTO> manualRequest(Long userId, ManualOrderRequestDTO dto) {
        ApiResponse<ManualOrderRequestResponseDTO> response = new ApiResponse<>();
        try {
            validateCoordsWeight(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng(), dto.getWeight());
            ManualOrderRequestEntity e = new ManualOrderRequestEntity();
            e.setUserId(userId);
            e.setPickupLat(dto.getPickupLat());
            e.setPickupLng(dto.getPickupLng());
            e.setDropLat(dto.getDropLat());
            e.setDropLng(dto.getDropLng());
            e.setWeight(dto.getWeight());
            e.setNote(dto.getNote());
            e.setStatus(ManualRequestStatus.PENDING);
            ManualOrderRequestEntity saved = manualOrderRequestRepository.save(e);
            response.setData(ManualOrderRequestResponseDTO.builder()
                    .id(saved.getId())
                    .status(saved.getStatus().name())
                    .build());
            response.setMessage("Request submitted for admin review");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<List<OrderResponseDTO>> listAllOrdersAdmin() {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            List<OrderResponseDTO> list = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                    .map(this::toOrderDto)
                    .collect(Collectors.toList());
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(list.size());
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> adminAssignRider(Long orderId, Long riderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found"));
            if (!RiderApprovalStatus.APPROVED.equals(rider.getApprovalStatus())) {
                throw new RuntimeException("Rider is not approved");
            }
            o.setRiderId(riderId);
            o.setStatus(OrderStatus.ASSIGNED);
            OrderEntity saved = orderRepository.save(o);
            notificationService.sendToRider(
                    riderId,
                    "New delivery assigned",
                    "Order #" + saved.getId() + " — open the app for details.",
                    NotificationService.baseData(saved.getId(), OrderStatus.ASSIGNED.name(), NotificationType.RIDER_JOB_ASSIGNED),
                    NotificationType.RIDER_JOB_ASSIGNED);
            response.setData(toOrderDto(saved));
            response.setMessage("Rider assigned");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> adminUpdateStatus(Long orderId, OrderStatus status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            o.setStatus(status);
            OrderEntity saved = orderRepository.save(o);
            notificationService.sendToUser(
                    saved.getUserId(),
                    "Order update",
                    "Order #" + saved.getId() + " is now " + status.name().replace('_', ' '),
                    NotificationService.baseData(saved.getId(), status.name(), NotificationType.USER_ORDER_STATUS_UPDATE),
                    NotificationType.USER_ORDER_STATUS_UPDATE);
            response.setData(toOrderDto(saved));
            response.setMessage("Status updated");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    private void notifyAfterOrderCreated(OrderEntity saved) {
        if (saved.getServiceMode() == ServiceMode.OUTSTATION) {
            notificationService.sendToAdminDevices(
                    "Outstation order needs rider",
                    "Order #" + saved.getId() + " — assign a rider (₹" + saved.getTotalAmount() + ").",
                    NotificationService.baseData(saved.getId(), OrderStatus.PENDING_ASSIGNMENT.name(),
                            NotificationType.ADMIN_OUTSTATION_PENDING_ASSIGNMENT),
                    NotificationType.ADMIN_OUTSTATION_PENDING_ASSIGNMENT);
            return;
        }
        if (saved.getRiderId() != null && saved.getStatus() == OrderStatus.ASSIGNED) {
            notificationService.sendToRider(
                    saved.getRiderId(),
                    "New delivery assigned",
                    "Order #" + saved.getId() + " — pickup nearby.",
                    NotificationService.baseData(saved.getId(), OrderStatus.ASSIGNED.name(), NotificationType.RIDER_JOB_ASSIGNED),
                    NotificationType.RIDER_JOB_ASSIGNED);
        }
    }

    /**
     * Nearest available approved rider by haversine distance to pickup (among {@code isAvailable} riders).
     */
    private Optional<Long> pickNearestAvailableRider(double lat, double lng) {
        return riderRepository.findByIsAvailableTrue().stream()
                .filter(r -> {
                    String ap = r.getApprovalStatus();
                    return ap == null || ap.isBlank() || RiderApprovalStatus.APPROVED.equalsIgnoreCase(ap);
                })
                .min(Comparator.comparingDouble(r -> {
                    double clat = r.getCurrentLat() != null ? r.getCurrentLat() : lat;
                    double clng = r.getCurrentLng() != null ? r.getCurrentLng() : lng;
                    return GeoUtils.haversineKm(lat, lng, clat, clng);
                }))
                .map(RiderEntity::getId);
    }

    private AppConfigEntity requireConfig() {
        return appConfigRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("Global config missing — ensure youdash_price_config row id=1 exists"));
    }

    private double resolveRouteRate(Long originHubId, Long destHubId, AppConfigEntity config) {
        return hubRouteRepository.findByOriginHubIdAndDestinationHubIdAndIsActiveTrue(originHubId, destHubId)
                .map(HubRouteEntity::getRatePerKm)
                .orElseGet(() -> nz(config.getDefaultRouteRatePerKm()));
    }

    private static OutstationDeliveryType parseOutstationDelivery(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("deliveryType is required for outstation (e.g. DOOR_TO_DOOR)");
        }
        try {
            return OutstationDeliveryType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid deliveryType: " + raw);
        }
    }

    private void validateCoordsWeight(Double pickupLat, Double pickupLng, Double dropLat, Double dropLng, Double weight) {
        if (pickupLat == null || pickupLng == null || dropLat == null || dropLng == null) {
            throw new RuntimeException("pickup and drop coordinates are required");
        }
        if (weight == null || weight <= 0) {
            throw new RuntimeException("weight must be > 0");
        }
    }

    private static void validateContactFields(CreateOrderRequestDTO dto) {
        if (isBlank(dto.getSenderName()) || isBlank(dto.getSenderPhone())
                || isBlank(dto.getReceiverName()) || isBlank(dto.getReceiverPhone())) {
            throw new RuntimeException("senderName, senderPhone, receiverName, and receiverPhone are required");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Prefer category default; otherwise use explicit request {@code deliveryType}.
     */
    private static String resolveDeliveryTypeForOrder(PackageCategoryEntity category, CreateOrderRequestDTO dto) {
        String fromCat = category.getDefaultDeliveryType();
        if (fromCat != null) {
            String t = fromCat.trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        if (dto.getDeliveryType() != null) {
            String t = dto.getDeliveryType().trim();
            if (!t.isEmpty()) {
                return t;
            }
        }
        return null;
    }

    private OrderResponseDTO toOrderDto(OrderEntity o) {
        return OrderResponseDTO.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .categoryId(o.getPackageCategoryId())
                .senderName(o.getSenderName())
                .senderPhone(o.getSenderPhone())
                .receiverName(o.getReceiverName())
                .receiverPhone(o.getReceiverPhone())
                .pickupLat(o.getPickupLat())
                .pickupLng(o.getPickupLng())
                .dropLat(o.getDropLat())
                .dropLng(o.getDropLng())
                .serviceMode(o.getServiceMode())
                .vehicleId(o.getVehicleId())
                .deliveryType(o.getDeliveryType())
                .originHubId(o.getOriginHubId())
                .destinationHubId(o.getDestinationHubId())
                .weight(o.getWeight())
                .paymentType(o.getPaymentType())
                .status(o.getStatus())
                .riderId(o.getRiderId())
                .subtotal(o.getSubtotal())
                .gstAmount(o.getGstAmount())
                .platformFee(o.getPlatformFee())
                .totalAmount(o.getTotalAmount())
                .couponAmount(o.getCouponAmount())
                .vehiclePricePerKm(o.getVehiclePricePerKm())
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .build();
    }

    private static void assertPricingAllOrNothing(CreateOrderRequestDTO dto) {
        int n = 0;
        if (dto.getSubtotal() != null) {
            n++;
        }
        if (dto.getGstAmount() != null) {
            n++;
        }
        if (dto.getPlatformFee() != null) {
            n++;
        }
        if (dto.getTotalAmount() != null) {
            n++;
        }
        if (n > 0 && n < 4) {
            throw new RuntimeException("Send all of subtotal, gstAmount, platformFee, totalAmount, or omit all for server pricing");
        }
    }

    private static boolean hasFullClientPricing(CreateOrderRequestDTO dto) {
        return dto.getSubtotal() != null && dto.getGstAmount() != null
                && dto.getPlatformFee() != null && dto.getTotalAmount() != null;
    }

    private void applyClientPricing(OrderEntity order, CreateOrderRequestDTO dto) {
        double preCoupon = dto.getTotalAmount();
        double couponDisc = resolveCouponAmount(dto.getCouponAmount(), preCoupon);
        order.setSubtotal(round2(dto.getSubtotal()));
        order.setGstAmount(round2(dto.getGstAmount()));
        order.setPlatformFee(round2(dto.getPlatformFee()));
        order.setCouponAmount(round2(couponDisc));
        order.setTotalAmount(round2(preCoupon - couponDisc));
    }

    private record LegKm(double pickupKm, double hubKm, double dropKm) {}

    private static LegKm outstationLegKm(
            double pickupDist, double hubDist, double dropDist, OutstationDeliveryType dtype) {
        double pk = pickupDist;
        double dk = dropDist;
        switch (dtype) {
            case DOOR_TO_DOOR -> {
            }
            case DOOR_TO_HUB -> dk = 0.0;
            case HUB_TO_DOOR -> pk = 0.0;
        }
        return new LegKm(round4(pk), round4(hubDist), round4(dk));
    }

    private static double resolveCouponAmount(Double rawCoupon, double baseTotal) {
        double c = nz(rawCoupon);
        if (c < 0) {
            throw new RuntimeException("couponAmount cannot be negative");
        }
        if (c > baseTotal + 0.01) {
            throw new RuntimeException("couponAmount cannot exceed order total");
        }
        return c;
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

    private void setError(ApiResponse<?> response, String message) {
        response.setMessage(message);
        response.setMessageKey("ERROR");
        response.setSuccess(false);
        response.setStatus(500);
    }
}

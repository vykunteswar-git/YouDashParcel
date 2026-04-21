package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.entity.*;
import com.youdash.entity.wallet.OrderRiderFinancialEntity;
import com.youdash.util.DeliveryOtpGenerator;
import com.youdash.exception.BadRequestException;
import com.youdash.model.*;
import com.youdash.repository.*;
import com.youdash.notification.NotificationType;
import com.youdash.service.DistanceService;
import com.youdash.service.DispatchService;
import com.youdash.service.NotificationService;
import com.youdash.service.OrderService;
import com.youdash.service.PricingService;
import com.youdash.service.ZoneService;
import com.youdash.service.CouponService;
import com.youdash.service.wallet.RiderWalletService;
import com.youdash.dto.coupon.CouponApplication;
import com.youdash.repository.wallet.OrderRiderFinancialRepository;
import com.youdash.security.RiderAccessVerifier;
import com.youdash.dto.wallet.OrderCompleteRequestDTO;
import com.youdash.model.wallet.CodCollectionMode;
import com.youdash.model.wallet.CodSettlementStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.youdash.dto.realtime.UserOrderEventDTO;
import com.youdash.realtime.RiderActiveOrderTopicPublisher;
import com.youdash.realtime.UserActiveOrderTopicPublisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Instant;

@Service
public class OrderServiceImpl implements OrderService {

    private static final int ADDRESS_SUGGESTION_DEFAULT_LIMIT = 7;
    private static final int ADDRESS_SUGGESTION_MAX_LIMIT = 7;
    private static final int ADDRESS_SUGGESTION_MAX_ORDERS_SCAN = 250;

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

    @Autowired
    private DispatchService dispatchService;

    @Autowired
    private RiderWalletService riderWalletService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private OrderRiderFinancialRepository orderRiderFinancialRepository;

    @Autowired
    private RiderAccessVerifier riderAccessVerifier;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RiderActiveOrderTopicPublisher riderActiveOrderTopicPublisher;

    @Autowired
    private UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Override
    public ApiResponse<FinalPriceResponseDTO> calculateFinal(Long userId, FinalPriceRequestDTO dto) {
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

            double preCouponTotal = round2(b.getTotal());
            double couponDiscount = 0.0;
            String appliedCode = null;
            if (StringUtils.hasText(dto.getCouponCode())) {
                CouponApplication cap = couponService.resolveApplication(
                        userId, dto.getCouponCode(), preCouponTotal, ServiceMode.OUTSTATION);
                couponDiscount = round2(cap.discountAmount());
                appliedCode = cap.normalizedCode();
            }

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
                    .total(round2(preCouponTotal - couponDiscount))
                    .couponDiscount(couponDiscount > 0 ? couponDiscount : null)
                    .appliedCouponCode(appliedCode)
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
    @Transactional
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
            AppConfigEntity checkoutConfig = requireConfig();
            validatePaymentModeEnabled(paymentType, checkoutConfig);

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
            order.setImageUrl(trimToNull(dto.getImageUrl()));
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
            CouponApplication promo = null;

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
                order.setDistanceKm(round4(dist));
                if (order.getVehiclePricePerKm() == null && vehicle.getPricePerKm() != null) {
                    order.setVehiclePricePerKm(round4(vehicle.getPricePerKm()));
                }

                if (clientPricing) {
                    if (StringUtils.hasText(dto.getCouponCode())) {
                        double preCoupon = resolvePreCouponTotalForClientPricing(dto);
                        CouponApplication cap = couponService.resolveApplication(
                                userId, dto.getCouponCode(), preCoupon, ServiceMode.INCITY);
                        promo = cap;
                        order.setSubtotal(round2(dto.getSubtotal()));
                        order.setGstAmount(round2(dto.getGstAmount()));
                        order.setPlatformFee(round2(dto.getPlatformFee()));
                        order.setCouponAmount(round2(cap.discountAmount()));
                        order.setTotalAmount(round2(preCoupon - cap.discountAmount()));
                        order.setAppliedCouponCode(cap.normalizedCode());
                    } else {
                        applyClientPricing(order, dto);
                    }
                } else {
                    AppConfigEntity cfg = checkoutConfig;
                    double sub = pricingService.incityVehicleTotal(dist, dto.getWeight(), vehicle);
                    double gst = sub * (nz(cfg.getGstPercent()) / 100.0);
                    double platform = nz(cfg.getPlatformFee());
                    double baseTotal = round2(sub + gst + platform);
                    double couponDisc;
                    if (StringUtils.hasText(dto.getCouponCode())) {
                        CouponApplication cap = couponService.resolveApplication(
                                userId, dto.getCouponCode(), baseTotal, ServiceMode.INCITY);
                        promo = cap;
                        couponDisc = cap.discountAmount();
                        order.setAppliedCouponCode(cap.normalizedCode());
                    } else {
                        couponDisc = resolveCouponAmount(dto.getCouponAmount(), baseTotal);
                    }
                    order.setSubtotal(round2(sub));
                    order.setGstAmount(round2(gst));
                    order.setPlatformFee(round2(platform));
                    order.setCouponAmount(round2(couponDisc));
                    order.setTotalAmount(round2(baseTotal - couponDisc));
                }

                // New INCITY flow: rider is not auto-assigned.
                order.setRiderId(null);
                order.setStatus(OrderStatus.SEARCHING_RIDER);
                order.setPaymentStatus("UNPAID");
                Instant now = Instant.now();
                order.setSearchExpiresAt(now.plusSeconds(30));
                order.setAcceptedAt(null);
                order.setPaymentDueAt(null);
                order.setCancelReason(null);
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
                order.setDistanceKm(round4(nz(order.getPickupDistanceKm()) + nz(order.getHubDistanceKm()) + nz(order.getDropDistanceKm())));

                if (clientPricing) {
                    if (StringUtils.hasText(dto.getCouponCode())) {
                        double preCoupon = resolvePreCouponTotalForClientPricing(dto);
                        CouponApplication cap = couponService.resolveApplication(
                                userId, dto.getCouponCode(), preCoupon, ServiceMode.OUTSTATION);
                        promo = cap;
                        order.setSubtotal(round2(dto.getSubtotal()));
                        order.setGstAmount(round2(dto.getGstAmount()));
                        order.setPlatformFee(round2(dto.getPlatformFee()));
                        order.setCouponAmount(round2(cap.discountAmount()));
                        order.setTotalAmount(round2(preCoupon - cap.discountAmount()));
                        order.setAppliedCouponCode(cap.normalizedCode());
                    } else {
                        applyClientPricing(order, dto);
                    }
                } else {
                    AppConfigEntity cfg = checkoutConfig;
                    double routeRate = resolveRouteRate(dto.getOriginHubId(), dto.getDestinationHubId(), cfg);
                    PricingService.OutstationBreakdown b = pricingService.outstationBreakdown(
                            pickupDist, hubDist, dropDist, routeRate, dto.getWeight(), dtype, cfg);
                    double baseOs = round2(b.getTotal());
                    double couponDiscOs;
                    if (StringUtils.hasText(dto.getCouponCode())) {
                        CouponApplication cap = couponService.resolveApplication(
                                userId, dto.getCouponCode(), baseOs, ServiceMode.OUTSTATION);
                        promo = cap;
                        couponDiscOs = cap.discountAmount();
                        order.setAppliedCouponCode(cap.normalizedCode());
                    } else {
                        couponDiscOs = resolveCouponAmount(dto.getCouponAmount(), baseOs);
                    }
                    order.setSubtotal(b.getSubtotal());
                    order.setGstAmount(b.getGstAmount());
                    order.setPlatformFee(b.getPlatformFee());
                    order.setCouponAmount(round2(couponDiscOs));
                    order.setTotalAmount(round2(baseOs - couponDiscOs));
                }
                // OUTSTATION stays admin-assigned; use CREATED until admin assigns.
                order.setStatus(OrderStatus.CREATED);
            }

            OrderEntity saved = orderRepository.save(order);
            saved.setDisplayOrderId("YP-" + saved.getId() + System.currentTimeMillis());
            if (saved.getPaymentType() == PaymentType.ONLINE && (saved.getPaymentStatus() == null || saved.getPaymentStatus().isBlank())) {
                saved.setPaymentStatus("UNPAID");
            }
            saved = orderRepository.save(saved);
            if (promo != null) {
                couponService.recordRedemption(promo.couponId(), userId, saved.getId());
                Map<String, String> couponData = new HashMap<>(
                        NotificationService.baseData(
                                saved.getId(),
                                saved.getStatus() != null ? saved.getStatus().name() : null,
                                NotificationType.USER_COUPON_APPLIED));
                couponData.put("couponCode", promo.normalizedCode());
                couponData.put("discountAmount", String.valueOf(promo.discountAmount()));
                notificationService.sendToUser(
                        userId,
                        "Coupon applied",
                        promo.normalizedCode() + " saved you ₹" + String.format("%.2f", promo.discountAmount()) + " on order " + saved.getId() + ".",
                        couponData,
                        NotificationType.USER_COUPON_APPLIED);
            }
            notifyAfterOrderCreated(saved);
            if (saved.getServiceMode() == ServiceMode.INCITY && saved.getStatus() == OrderStatus.SEARCHING_RIDER) {
                dispatchService.dispatchNewIncityOrder(saved);
            }
            response.setData(toOrderDto(saved, null, null, false));
            response.setMessage("Order created");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            notifyOrderCreationFailed(userId, e.getMessage());
            setError(response, e.getMessage());
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> getOrder(Long orderId, Long tokenUserId, String tokenType, boolean admin) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!admin) {
                if ("RIDER".equals(tokenType)) {
                    if (o.getRiderId() == null || !Objects.equals(o.getRiderId(), tokenUserId)) {
                        throw new RuntimeException("Access denied");
                    }
                } else if (!Objects.equals(o.getUserId(), tokenUserId)) {
                    throw new RuntimeException("Access denied");
                }
            }
            boolean riderOrderApi = "RIDER".equals(tokenType);
            OrderResponseDTO data = toOrderDto(o, null, null, riderOrderApi);
            if (riderOrderApi) {
                data = stripCommercialDetailsForRider(data);
                data.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(o));
            }
            response.setData(data);
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
    public ApiResponse<List<OrderResponseDTO>> listRiderOrders(Long riderId) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            List<OrderEntity> orders = orderRepository.findByRiderIdOrderByCreatedAtDesc(riderId, PageRequest.of(0, 200));
            Set<Long> riderIds = orders.stream()
                    .map(OrderEntity::getRiderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, RiderEntity> riderMap = riderIds.isEmpty()
                    ? Map.of()
                    : riderRepository.findAllById(riderIds).stream()
                            .collect(Collectors.toMap(RiderEntity::getId, Function.identity()));
            Map<Long, VehicleEntity> vehicleMap = buildVehicleBatchForOrders(orders, riderMap);
            List<Long> orderIds = orders.stream().map(OrderEntity::getId).filter(Objects::nonNull).toList();
            Map<Long, Double> settledEarningByOrderId = orderIds.isEmpty()
                    ? Map.of()
                    : orderRiderFinancialRepository.findByOrderIdIn(orderIds).stream()
                            .collect(Collectors.toMap(OrderRiderFinancialEntity::getOrderId, OrderRiderFinancialEntity::getRiderEarningAmount, (a, b) -> a));
            List<OrderResponseDTO> list = new ArrayList<>(orders.size());
            for (OrderEntity o : orders) {
                OrderResponseDTO d = stripCommercialDetailsForRider(toOrderDto(o, riderMap, vehicleMap, true));
                Double settled = settledEarningByOrderId.get(o.getId());
                d.setEarnedAmount(settled != null ? settled : riderWalletService.estimateRiderEarningForOrder(o));
                list.add(d);
            }
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
    public ApiResponse<List<OrderResponseDTO>> listUserOrders(Long userId, Long tokenUserId, boolean admin) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            if (!admin && !Objects.equals(userId, tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
            List<OrderEntity> orders = admin
                    ? orderRepository.findByUserIdOrderByCreatedAtDesc(userId)
                    : orderRepository.findByUserIdAndStatusNotOrderByCreatedAtDesc(userId, OrderStatus.EXPIRED);
            Set<Long> riderIds = orders.stream()
                    .map(OrderEntity::getRiderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, RiderEntity> riderMap = riderIds.isEmpty()
                    ? Map.of()
                    : riderRepository.findAllById(riderIds).stream()
                            .collect(Collectors.toMap(RiderEntity::getId, Function.identity()));
            Map<Long, VehicleEntity> vehicleMap = buildVehicleBatchForOrders(orders, riderMap);
            List<OrderResponseDTO> list = orders.stream()
                    .map(o -> toOrderDto(o, riderMap, vehicleMap, false))
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
    public ApiResponse<List<OrderAddressSuggestionDTO>> listUserOrderAddressSuggestions(
            Long userId, Long tokenUserId, boolean admin, Integer limit) {
        ApiResponse<List<OrderAddressSuggestionDTO>> response = new ApiResponse<>();
        try {
            if (!admin && !Objects.equals(userId, tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
            int cap = ADDRESS_SUGGESTION_DEFAULT_LIMIT;
            if (limit != null) {
                cap = Math.min(ADDRESS_SUGGESTION_MAX_LIMIT, Math.max(1, limit));
            }
            var page = PageRequest.of(0, ADDRESS_SUGGESTION_MAX_ORDERS_SCAN, Sort.by(Sort.Direction.DESC, "createdAt"));
            List<OrderEntity> orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, page);
            List<OrderAddressSuggestionDTO> suggestions = buildAddressSuggestions(orders, cap);
            response.setData(suggestions);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount(suggestions.size());
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    private static List<OrderAddressSuggestionDTO> buildAddressSuggestions(List<OrderEntity> orders, int maxSuggestions) {
        Set<String> seen = new HashSet<>();
        List<OrderAddressSuggestionDTO> out = new ArrayList<>();
        for (OrderEntity o : orders) {
            if (out.size() >= maxSuggestions) {
                break;
            }
            tryAddSuggestion(out, seen, o, OrderAddressRole.PICKUP, o.getPickupLat(), o.getPickupLng(), o.getSenderName(), o.getSenderPhone(), o.getCreatedAt());
            if (out.size() >= maxSuggestions) {
                break;
            }
            tryAddSuggestion(out, seen, o, OrderAddressRole.DROP, o.getDropLat(), o.getDropLng(), o.getReceiverName(), o.getReceiverPhone(), o.getCreatedAt());
        }
        out.sort(Comparator.comparing(OrderAddressSuggestionDTO::getLastUsedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        return out;
    }

    private static void tryAddSuggestion(
            List<OrderAddressSuggestionDTO> out,
            Set<String> seen,
            OrderEntity o,
            OrderAddressRole role,
            Double lat,
            Double lng,
            String contactName,
            String contactPhone,
            Instant createdAt) {
        if (lat == null || lng == null) {
            return;
        }
        String key = role.name() + '|' + String.format(Locale.ROOT, "%.5f,%.5f", lat, lng);
        if (!seen.add(key)) {
            return;
        }
        out.add(OrderAddressSuggestionDTO.builder()
                .role(role)
                .lat(lat)
                .lng(lng)
                .contactName(contactName)
                .contactPhone(contactPhone)
                .lastUsedAt(createdAt != null ? createdAt.toString() : null)
                .build());
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
            Map<String, String> manualUserData = new HashMap<>();
            manualUserData.put("manualRequestId", String.valueOf(saved.getId()));
            manualUserData.put("type", NotificationType.USER_MANUAL_REQUEST_SUBMITTED.name());
            notificationService.sendToUser(
                    userId,
                    "Request received",
                    "Manual order request #" + saved.getId() + " is pending admin review.",
                    manualUserData,
                    NotificationType.USER_MANUAL_REQUEST_SUBMITTED);
            Map<String, String> manualAdminData = new HashMap<>();
            manualAdminData.put("manualRequestId", String.valueOf(saved.getId()));
            manualAdminData.put("userId", String.valueOf(userId));
            manualAdminData.put("type", NotificationType.ADMIN_MANUAL_REQUEST_NEW.name());
            notificationService.sendToAdminDevices(
                    "New manual order request",
                    "Request #" + saved.getId() + " from user " + userId + " — review in admin.",
                    manualAdminData,
                    NotificationType.ADMIN_MANUAL_REQUEST_NEW);
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
            List<OrderEntity> allOrders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
            Set<Long> allRiderIds = allOrders.stream()
                    .map(OrderEntity::getRiderId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, RiderEntity> riderMap = allRiderIds.isEmpty()
                    ? Map.of()
                    : riderRepository.findAllById(allRiderIds).stream()
                            .collect(Collectors.toMap(RiderEntity::getId, Function.identity()));
            Map<Long, VehicleEntity> vehicleMap = buildVehicleBatchForOrders(allOrders, riderMap);
            List<OrderResponseDTO> list = allOrders.stream()
                    .map(o -> toOrderDto(o, riderMap, vehicleMap, false))
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
            // OUTSTATION assignment or manual assignment: treat as confirmed (ready to proceed).
            o.setStatus(OrderStatus.CONFIRMED);
            if (o.getDeliveryOtp() == null || o.getDeliveryOtp().isBlank()) {
                o.setDeliveryOtp(DeliveryOtpGenerator.generate());
            }
            OrderEntity saved = orderRepository.save(o);
            notificationService.sendToRider(
                    riderId,
                    "New delivery assigned",
                    "Order #" + saved.getId() + " — open the app for details.",
                    NotificationService.baseData(saved.getId(), OrderStatus.CONFIRMED.name(), NotificationType.RIDER_JOB_ASSIGNED),
                    NotificationType.RIDER_JOB_ASSIGNED);
            response.setData(toOrderDto(saved, null, null, false));
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
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> adminUpdateStatus(Long orderId, OrderStatus status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity o = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (status == OrderStatus.DELIVERED && o.getPaymentType() == PaymentType.COD) {
            if (o.getCodCollectionMode() == null || o.getCodCollectedAmount() == null) {
                throw new RuntimeException("COD details missing. Use /order/complete");
            }
        }
        o.setStatus(status);
        OrderEntity saved = orderRepository.save(o);
        if (status == OrderStatus.DELIVERED && saved.getRiderId() != null) {
            riderWalletService.settleOrderDelivered(
                    saved,
                    saved.getPaymentType() == PaymentType.COD ? saved.getCodCollectionMode() : null,
                    saved.getCodCollectedAmount(),
                    null,
                    "ADMIN");
        }
        notificationService.sendToUser(
                saved.getUserId(),
                "Order update",
                "Order #" + saved.getId() + " is now " + status.name().replace('_', ' '),
                NotificationService.baseData(saved.getId(), status.name(), NotificationType.USER_ORDER_STATUS_UPDATE),
                NotificationType.USER_ORDER_STATUS_UPDATE);
        response.setData(toOrderDto(saved, null, null, false));
        response.setMessage("Status updated");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> completeOrderForRider(Long tokenUserId, String tokenType, OrderCompleteRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (!"RIDER".equals(tokenType)) {
            throw new BadRequestException("Rider token required");
        }
        if (dto == null || dto.getOrderId() == null || dto.getOrderId().isBlank()) {
            throw new BadRequestException("orderId is required");
        }
        Long actingRiderId = riderAccessVerifier.resolveActingRiderIdFromToken(tokenUserId, tokenType);

        OrderEntity o = resolveOrderByIdOrReference(dto.getOrderId().trim());
        if (o.getRiderId() == null || !Objects.equals(o.getRiderId(), actingRiderId)) {
            throw new BadRequestException("Access denied");
        }

        boolean financialExists = riderWalletService.hasOrderRiderFinancial(o.getId());
        boolean alreadyDelivered = o.getStatus() == OrderStatus.DELIVERED;

        if (alreadyDelivered && financialExists) {
            OrderEntity refreshed = orderRepository.findById(o.getId()).orElse(o);
            markRiderAvailableAfterDelivery(refreshed.getRiderId());
            response.setData(stripCommercialDetailsForRider(toOrderDto(refreshed, null, null, true)));
            response.setMessage("Order completed");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            return response;
        }

        if (!alreadyDelivered) {
            if (o.getStatus() != OrderStatus.IN_TRANSIT) {
                throw new BadRequestException("Order cannot be completed from status: " + o.getStatus());
            }
            if (!Boolean.TRUE.equals(o.getIsOtpVerified())) {
                throw new BadRequestException("OTP not verified");
            }
        }

        if (o.getPaymentType() == null) {
            throw new BadRequestException("Order payment type is missing");
        }

        if (o.getPaymentType() == PaymentType.ONLINE) {
            // Amount was settled at payment gateway; order row holds totalAmount + PAID — body only needs orderId.
            if (!alreadyDelivered && !isOrderPaidOnline(o)) {
                throw new BadRequestException("Online order must be paid before marking delivered");
            }
            o.setCodCollectedAmount(null);
            o.setCodCollectionMode(null);
            o.setCodSettlementStatus(CodSettlementStatus.SETTLED);
        } else if (o.getPaymentType() == PaymentType.COD) {
            if (dto.getCodCollectionMode() == null || dto.getCodCollectionMode().isBlank()) {
                throw new BadRequestException("codCollectionMode is required for COD (CASH or QR)");
            }
            if (dto.getCodCollectedAmount() == null) {
                throw new BadRequestException("codCollectedAmount is required for COD orders");
            }
            CodCollectionMode mode = CodCollectionMode.valueOf(dto.getCodCollectionMode().trim().toUpperCase());
            double collected = dto.getCodCollectedAmount();
            if (collected <= 0) {
                throw new BadRequestException("codCollectedAmount must be > 0");
            }
            double orderTotal = nz(o.getTotalAmount());
            if (collected > orderTotal * 1.2 + 0.0001) {
                throw new BadRequestException("Invalid COD amount: exceeds expected range");
            }
            o.setCodCollectionMode(mode);
            o.setCodCollectedAmount(round2(collected));
            o.setCodSettlementStatus(CodSettlementStatus.PENDING);
        } else {
            throw new BadRequestException("Unsupported payment type: " + o.getPaymentType());
        }

        if (!alreadyDelivered) {
            o.setStatus(OrderStatus.DELIVERED);
        }
        OrderEntity saved = orderRepository.save(o);

        riderWalletService.settleOrderDelivered(
                saved,
                saved.getPaymentType() == PaymentType.COD ? saved.getCodCollectionMode() : null,
                saved.getCodCollectedAmount(),
                actingRiderId,
                "RIDER");

        UserOrderEventDTO deliveredEvt = new UserOrderEventDTO();
        deliveredEvt.setOrderId(saved.getId());
        deliveredEvt.setEvent("delivered");
        deliveredEvt.setEventType("status_updated");
        deliveredEvt.setStatus(OrderStatus.DELIVERED.name());
        deliveredEvt.setRiderId(saved.getRiderId());
        messagingTemplate.convertAndSend("/topic/users/" + saved.getUserId() + "/order-events", deliveredEvt);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(), saved.getId(), OrderStatus.DELIVERED.name(), saved.getRiderId());
        userActiveOrderTopicPublisher.publishReleased(saved.getUserId());

        notificationService.sendToUser(
                saved.getUserId(),
                "Order update",
                "Order #" + saved.getId() + " is now " + OrderStatus.DELIVERED.name().replace('_', ' '),
                NotificationService.baseData(saved.getId(), OrderStatus.DELIVERED.name(), NotificationType.USER_ORDER_STATUS_UPDATE),
                NotificationType.USER_ORDER_STATUS_UPDATE);

        markRiderAvailableAfterDelivery(saved.getRiderId());

        riderActiveOrderTopicPublisher.publish(actingRiderId, saved.getId(), OrderStatus.DELIVERED, "delivered");

        OrderEntity refreshed = orderRepository.findById(saved.getId()).orElse(saved);
        response.setData(stripCommercialDetailsForRider(toOrderDto(refreshed, null, null, true)));
        response.setMessage("Order completed");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> verifyDeliveryOtp(
            Long orderId, VerifyDeliveryOtpRequestDTO dto, Long tokenUserId, String tokenType) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (orderId == null) {
            throw new BadRequestException("orderId is required");
        }
        if (dto == null || dto.getOtp() == null || dto.getOtp().isBlank()) {
            throw new BadRequestException("otp is required");
        }
        OrderEntity o = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found"));

        boolean allowed = false;
        if ("USER".equals(tokenType) && Objects.equals(o.getUserId(), tokenUserId)) {
            allowed = true;
        } else if ("RIDER".equals(tokenType)) {
            Long actingRiderId = riderAccessVerifier.resolveActingRiderIdFromToken(tokenUserId, tokenType);
            allowed = Objects.equals(o.getRiderId(), actingRiderId);
        }
        if (!allowed) {
            throw new BadRequestException("Access denied");
        }

        if (o.getStatus() != OrderStatus.IN_TRANSIT) {
            throw new BadRequestException("OTP can only be verified while order is IN_TRANSIT");
        }
        if (o.getDeliveryOtp() == null) {
            throw new BadRequestException("No delivery OTP on this order");
        }
        String provided = dto.getOtp().trim();
        if (!o.getDeliveryOtp().equals(provided)) {
            throw new BadRequestException("Invalid OTP");
        }
        o.setIsOtpVerified(true);
        OrderEntity saved = orderRepository.save(o);

        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(saved.getId());
        evt.setEvent("otp_verified");
        evt.setEventType("otp_verified");
        evt.setStatus(OrderStatus.IN_TRANSIT.name());
        evt.setRiderId(saved.getRiderId());
        messagingTemplate.convertAndSend("/topic/users/" + saved.getUserId() + "/order-events", evt);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(), saved.getId(), OrderStatus.IN_TRANSIT.name(), saved.getRiderId());

        if (saved.getRiderId() != null) {
            riderActiveOrderTopicPublisher.publish(
                    saved.getRiderId(), saved.getId(), OrderStatus.IN_TRANSIT, "otp_verified");
        }

        if ("RIDER".equals(tokenType) && saved.getUserId() != null) {
            Map<String, String> userOtpData = new HashMap<>(
                    NotificationService.baseData(
                            saved.getId(),
                            OrderStatus.IN_TRANSIT.name(),
                            NotificationType.USER_OTP_VERIFIED_BY_RIDER));
            if (saved.getRiderId() != null) {
                userOtpData.put("riderId", String.valueOf(saved.getRiderId()));
            }
            notificationService.sendToUser(
                    saved.getUserId(),
                    "Delivery OTP verified",
                    "Rider verified the OTP for order #" + saved.getId() + ".",
                    userOtpData,
                    NotificationType.USER_OTP_VERIFIED_BY_RIDER);
        } else if ("USER".equals(tokenType) && saved.getRiderId() != null) {
            Map<String, String> riderOtpData = new HashMap<>(
                    NotificationService.baseData(
                            saved.getId(),
                            OrderStatus.IN_TRANSIT.name(),
                            NotificationType.RIDER_OTP_VERIFIED_BY_USER));
            notificationService.sendToRider(
                    saved.getRiderId(),
                    "Delivery OTP verified",
                    "Customer verified the OTP for order #" + saved.getId() + ". You can complete delivery.",
                    riderOtpData,
                    NotificationType.RIDER_OTP_VERIFIED_BY_USER);
        }

        boolean riderApi = "RIDER".equals(tokenType);
        OrderResponseDTO data = toOrderDto(saved, null, null, riderApi);
        if (riderApi) {
            data = stripCommercialDetailsForRider(data);
        }
        response.setData(data);
        response.setMessage("OTP verified");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> cancelOrder(Long orderId, Long tokenUserId, String tokenType) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (orderId == null) {
            throw new RuntimeException("orderId is required");
        }
        OrderEntity o = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (tokenUserId == null) {
            throw new RuntimeException("Unauthorized");
        }
        boolean admin = "ADMIN".equals(tokenType);
        if (!admin) {
            if (!"USER".equals(tokenType) || !Objects.equals(o.getUserId(), tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
        }
        if (o.getServiceMode() != ServiceMode.INCITY) {
            throw new RuntimeException("Cancel API is supported only for INCITY orders");
        }
        if (o.getStatus() == OrderStatus.DELIVERED) {
            throw new RuntimeException("Delivered orders cannot be cancelled");
        }
        if (o.getStatus() == OrderStatus.CANCELLED || o.getStatus() == OrderStatus.EXPIRED) {
            response.setData(toOrderDto(o, null, null, false));
            response.setMessage("Order already closed");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            return response;
        }

        OrderStatus expected = o.getStatus();
        int updated = orderRepository.updateStatusWithReason(
                o.getId(),
                ServiceMode.INCITY,
                expected,
                OrderStatus.CANCELLED,
                "USER_CANCELLED",
                "PAID".equalsIgnoreCase(o.getPaymentStatus()) ? o.getPaymentStatus() : "FAILED");
        if (updated == 1) {
            if (o.getRiderId() != null) {
                riderRepository.release(o.getRiderId());
                riderActiveOrderTopicPublisher.publish(
                        o.getRiderId(), o.getId(), OrderStatus.CANCELLED, "released", "USER_CANCELLED");
            }
            dispatchService.closeRequest(o.getId(), "cancelled", null);
            userActiveOrderTopicPublisher.publishReleased(o.getUserId());
            OrderEntity refreshedForPush = orderRepository.findById(o.getId()).orElse(o);
            Map<String, String> closedData = new HashMap<>(
                    NotificationService.baseData(
                            refreshedForPush.getId(),
                            OrderStatus.CANCELLED.name(),
                            NotificationType.USER_ORDER_CLOSED));
            closedData.put("cancelReason", "USER_CANCELLED");
            notificationService.sendToUser(
                    refreshedForPush.getUserId(),
                    "Order cancelled",
                    "Order #" + refreshedForPush.getId() + " was cancelled.",
                    closedData,
                    NotificationType.USER_ORDER_CLOSED);
        }

        OrderEntity refreshed = orderRepository.findById(o.getId()).orElse(o);
        response.setData(toOrderDto(refreshed, null, null, false));
        response.setMessage("Order cancelled");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    private OrderEntity resolveOrderByIdOrReference(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("orderId is required");
        }
        String s = raw.trim();
        if (s.regionMatches(true, 0, "YP-", 0, 3)) {
            return orderRepository.findByDisplayOrderId(s)
                    .orElseThrow(() -> new RuntimeException("Order not found with display id: " + s));
        }
        try {
            long id = Long.parseLong(s);
            return orderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid orderId: " + s);
        }
    }

    private void notifyAfterOrderCreated(OrderEntity saved) {
        if (saved.getUserId() == null) {
            return;
        }
        String ref = saved.getDisplayOrderId() != null ? saved.getDisplayOrderId() : "#" + saved.getId();
        if (saved.getPaymentType() == PaymentType.COD) {
            notificationService.sendToUser(
                    saved.getUserId(),
                    "Order placed",
                    "COD order " + ref + " is confirmed. We're finding a rider.",
                    NotificationService.baseData(
                            saved.getId(),
                            saved.getStatus() != null ? saved.getStatus().name() : null,
                            NotificationType.ORDER_PLACED_COD),
                    NotificationType.ORDER_PLACED_COD);
            notificationService.sendToAdminDevices(
                    "New COD order",
                    "Order " + ref + " — COD ₹" + saved.getTotalAmount() + " (user " + saved.getUserId() + ").",
                    NotificationService.baseData(
                            saved.getId(),
                            saved.getStatus() != null ? saved.getStatus().name() : null,
                            NotificationType.ADMIN_COD_ORDER),
                    NotificationType.ADMIN_COD_ORDER);
        } else {
            notificationService.sendToUser(
                    saved.getUserId(),
                    "Order placed",
                    "Order " + ref + " was created successfully.",
                    NotificationService.baseData(
                            saved.getId(),
                            saved.getStatus() != null ? saved.getStatus().name() : null,
                            NotificationType.ORDER_CREATED),
                    NotificationType.ORDER_CREATED);
        }
        if (saved.getServiceMode() == ServiceMode.OUTSTATION) {
            notificationService.sendToAdminDevices(
                    "Outstation order needs rider",
                    "Order #" + saved.getId() + " — assign a rider (₹" + saved.getTotalAmount() + ").",
                    NotificationService.baseData(saved.getId(), OrderStatus.CREATED.name(),
                            NotificationType.ADMIN_OUTSTATION_PENDING_ASSIGNMENT),
                    NotificationType.ADMIN_OUTSTATION_PENDING_ASSIGNMENT);
        }
        // INCITY: dispatch service handles rider notifications (socket/push). No auto-assign here.
    }

    private void notifyOrderCreationFailed(Long userId, String message) {
        if (userId == null) {
            return;
        }
        Map<String, String> data = new HashMap<>(
                NotificationService.baseData(null, "FAILED", NotificationType.ORDER_CREATE_FAILED));
        if (message != null && !message.isBlank()) {
            data.put("reason", message);
        }
        notificationService.sendToUser(
                userId,
                "Order could not be placed",
                message != null && !message.isBlank() ? message : "Please try again.",
                data,
                NotificationType.ORDER_CREATE_FAILED);
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

    private static void validatePaymentModeEnabled(PaymentType paymentType, AppConfigEntity config) {
        if (paymentType == PaymentType.COD && !Boolean.TRUE.equals(config.getCodEnabled())) {
            throw new RuntimeException("COD is currently disabled");
        }
        if (paymentType == PaymentType.ONLINE && !Boolean.TRUE.equals(config.getOnlineEnabled())) {
            throw new RuntimeException("Online payment is currently disabled");
        }
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

    private Map<Long, VehicleEntity> buildVehicleBatchForOrders(List<OrderEntity> orders, Map<Long, RiderEntity> riderMap) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        Set<Long> ids = new HashSet<>();
        for (OrderEntity o : orders) {
            RiderEntity r = o.getRiderId() != null && riderMap != null ? riderMap.get(o.getRiderId()) : null;
            Long vid = resolveEffectiveVehicleId(o, r);
            if (vid != null) {
                ids.add(vid);
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return vehicleRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(VehicleEntity::getId, Function.identity()));
    }

    /** Rider's catalog vehicle when assigned; otherwise the order's booked vehicle (INCITY). */
    private static Long resolveEffectiveVehicleId(OrderEntity o, RiderEntity rider) {
        if (rider != null && rider.getVehicleId() != null) {
            return rider.getVehicleId();
        }
        return o.getVehicleId();
    }

    OrderResponseDTO toOrderDto(OrderEntity o) {
        return toOrderDto(o, null, null, false);
    }

    /** Full mapping for rider apps: narrower OTP rules + strips GST/platform fields. */
    OrderResponseDTO toOrderDtoForRider(OrderEntity o) {
        return stripCommercialDetailsForRider(toOrderDto(o, null, null, true));
    }

    /**
     * @param riderBatch      optional map of rider id → entity for batch APIs; {@code null} loads rider individually.
     * @param vehicleBatch    optional map of vehicle id → entity; {@code null} loads vehicle individually when needed.
     * @param riderOrderApi   {@code true} for rider-facing endpoints (OTP only in {@code IN_TRANSIT} until verified).
     *                        {@code false} for customer/admin — OTP shown from accept/payment through trip until verified.
     */
    OrderResponseDTO toOrderDto(OrderEntity o, Map<Long, RiderEntity> riderBatch, Map<Long, VehicleEntity> vehicleBatch, boolean riderOrderApi) {
        RiderEntity rider = resolveRiderForOrderDto(o.getRiderId(), riderBatch);
        String riderName = rider != null ? rider.getName() : null;
        String riderPhone = rider != null ? rider.getPhone() : null;
        String deliveryOtp = resolveDeliveryOtpForResponse(o, riderOrderApi);

        Long effectiveVehicleId = resolveEffectiveVehicleId(o, rider);
        VehicleEntity vehicleRow = null;
        if (effectiveVehicleId != null) {
            if (vehicleBatch != null) {
                vehicleRow = vehicleBatch.get(effectiveVehicleId);
            }
            if (vehicleRow == null) {
                vehicleRow = vehicleRepository.findById(effectiveVehicleId).orElse(null);
            }
        }
        String vehicleName = vehicleRow != null ? vehicleRow.getName() : null;
        String vehicleImageUrl = vehicleRow != null ? vehicleRow.getImageUrl() : null;
        String vehicleNumber = rider != null ? trimToNull(rider.getVehicleNumber()) : null;

        return OrderResponseDTO.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .categoryId(o.getPackageCategoryId())
                .senderName(o.getSenderName())
                .senderPhone(o.getSenderPhone())
                .receiverName(o.getReceiverName())
                .receiverPhone(o.getReceiverPhone())
                .imageUrl(o.getImageUrl())
                .pickupLat(o.getPickupLat())
                .pickupLng(o.getPickupLng())
                .dropLat(o.getDropLat())
                .dropLng(o.getDropLng())
                .serviceMode(o.getServiceMode())
                .vehicleId(effectiveVehicleId)
                .vehicleName(vehicleName)
                .vehicleImageUrl(vehicleImageUrl)
                .vehicleNumber(vehicleNumber)
                .deliveryType(o.getDeliveryType())
                .originHubId(o.getOriginHubId())
                .destinationHubId(o.getDestinationHubId())
                .weight(o.getWeight())
                .distanceKm(o.getDistanceKm())
                .paymentType(o.getPaymentType())
                .status(o.getStatus())
                .riderId(o.getRiderId())
                .riderName(riderName)
                .riderPhone(riderPhone)
                .deliveryOtp(deliveryOtp)
                .isOtpVerified(o.getIsOtpVerified())
                .subtotal(o.getSubtotal())
                .gstAmount(o.getGstAmount())
                .platformFee(o.getPlatformFee())
                .totalAmount(o.getTotalAmount())
                .couponAmount(o.getCouponAmount())
                .appliedCouponCode(o.getAppliedCouponCode())
                .vehiclePricePerKm(o.getVehiclePricePerKm())
                .displayOrderId(o.getDisplayOrderId())
                .paymentStatus(o.getPaymentStatus())
                .razorpayOrderId(o.getRazorpayOrderId())
                .codCollectedAmount(o.getCodCollectedAmount())
                .codCollectionMode(o.getCodCollectionMode())
                .codSettlementStatus(o.getCodSettlementStatus())
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .build();
    }

    /**
     * Customer/admin: OTP from post-accept job states until verified. Rider app: only {@code IN_TRANSIT} (handover).
     */
    private static String resolveDeliveryOtpForResponse(OrderEntity o, boolean riderOrderApi) {
        if (Boolean.TRUE.equals(o.getIsOtpVerified())) {
            return null;
        }
        String otp = o.getDeliveryOtp();
        if (otp == null || otp.isBlank()) {
            return null;
        }
        OrderStatus s = o.getStatus();
        if (riderOrderApi) {
            return s == OrderStatus.IN_TRANSIT ? otp : null;
        }
        return switch (s) {
            case RIDER_ACCEPTED, PAYMENT_PENDING, CONFIRMED, PICKED_UP, IN_TRANSIT -> otp;
            default -> null;
        };
    }

    /**
     * Riders do not need GST, platform fee, coupon, or gateway pricing components — only operational fields
     * (e.g. {@code totalAmount}, {@code distanceKm}, addresses, status).
     */
    private static OrderResponseDTO stripCommercialDetailsForRider(OrderResponseDTO dto) {
        if (dto == null) {
            return null;
        }
        dto.setSubtotal(null);
        dto.setGstAmount(null);
        dto.setPlatformFee(null);
        dto.setCouponAmount(null);
        dto.setVehiclePricePerKm(null);
        dto.setRazorpayOrderId(null);
        // Rider should primarily see earnings; keep totalAmount only for COD collection context.
        if (dto.getPaymentType() != PaymentType.COD) {
            dto.setTotalAmount(null);
            dto.setCodCollectedAmount(null);
            dto.setCodCollectionMode(null);
            dto.setCodSettlementStatus(null);
        }
        return dto;
    }

    private RiderEntity resolveRiderForOrderDto(Long riderId, Map<Long, RiderEntity> riderBatch) {
        if (riderId == null) {
            return null;
        }
        if (riderBatch != null) {
            return riderBatch.get(riderId);
        }
        return riderRepository.findById(riderId).orElse(null);
    }

    private void markRiderAvailableAfterDelivery(Long riderId) {
        if (riderId == null) {
            return;
        }
        riderRepository.findById(riderId).ifPresent(r -> {
            r.setIsAvailable(true);
            riderRepository.save(r);
        });
    }

    /** ONLINE pre-paid orders: wallet settlement uses {@code order.totalAmount}; gateway payment is reflected as PAID. */
    private static boolean isOrderPaidOnline(OrderEntity o) {
        String ps = o.getPaymentStatus();
        return ps != null && "PAID".equalsIgnoreCase(ps.trim());
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

    /**
     * Prevent double discount when client sends payable total from preview screen.
     * For coupon-code flow, trust subtotal+gst+platform as pre-coupon total.
     */
    private static double resolvePreCouponTotalForClientPricing(CreateOrderRequestDTO dto) {
        double computed = round2(nz(dto.getSubtotal()) + nz(dto.getGstAmount()) + nz(dto.getPlatformFee()));
        if (computed > 0) {
            return computed;
        }
        return round2(nz(dto.getTotalAmount()));
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

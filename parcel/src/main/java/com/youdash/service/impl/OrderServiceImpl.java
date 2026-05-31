package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.*;
import com.youdash.entity.*;
import com.youdash.util.DeliveryOtpGenerator;
import com.youdash.util.OutstationCodPolicy;
import com.youdash.util.OutstationHubHandover;
import com.youdash.util.OutstationPayableLegSplit;
import com.youdash.util.OutstationRiderLegPolicy;
import com.youdash.exception.BadRequestException;
import com.youdash.model.*;
import com.youdash.repository.*;
import com.youdash.notification.NotificationType;
import com.youdash.service.DistanceService;
import com.youdash.service.DispatchService;
import com.youdash.service.NotificationService;
import com.youdash.service.OrderService;
import com.youdash.service.OrderStatusTransitionGuard;
import com.youdash.service.OrderTimelineService;
import com.youdash.service.PricingService;
import com.youdash.service.ZoneService;
import com.youdash.service.RouteRateResolver;
import com.youdash.service.CouponService;
import com.youdash.service.wallet.RiderWalletService;
import com.youdash.dto.coupon.CouponApplication;
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
import com.youdash.realtime.AdminOrderTopicPublisher;
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
    private RouteRateResolver routeRateResolver;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderAddressPreferenceRepository orderAddressPreferenceRepository;

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
    private RiderAccessVerifier riderAccessVerifier;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RiderActiveOrderTopicPublisher riderActiveOrderTopicPublisher;

    @Autowired
    private UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Autowired
    private AdminOrderTopicPublisher adminOrderTopicPublisher;

    @Autowired
    private RiderRatingRepository riderRatingRepository;

    @Autowired
    private OrderTimelineService orderTimelineService;

    @Autowired
    private OrderStatusTransitionGuard orderStatusTransitionGuard;

    @Override
    public ApiResponse<FinalPriceResponseDTO> calculateFinal(Long userId, FinalPriceRequestDTO dto) {
        ApiResponse<FinalPriceResponseDTO> response = new ApiResponse<>();
        try {
            validateCoordsWeight(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng(),
                    dto.getWeight());
            zoneService.inactiveZoneBlockMessage(
                            dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng())
                    .ifPresent(msg -> {
                        throw new RuntimeException(msg);
                    });
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
            requireOutstationHubsMatchZones(
                    dto.getPickupLat(), dto.getPickupLng(),
                    dto.getDropLat(), dto.getDropLng(),
                    origin, dest);

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
            validateCoordsWeight(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng(),
                    dto.getWeight());
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

            zoneService.inactiveZoneBlockMessage(
                            dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng())
                    .ifPresent(msg -> {
                        throw new RuntimeException(msg);
                    });

            Optional<com.youdash.entity.ZoneEntity> pz = zoneService.findZoneContaining(dto.getPickupLat(),
                    dto.getPickupLng());
            Optional<com.youdash.entity.ZoneEntity> dz = zoneService.findZoneContaining(dto.getDropLat(),
                    dto.getDropLng());
            boolean sameZone = pz.isPresent() && dz.isPresent()
                    && pz.get().getId().equals(dz.get().getId());

            OrderEntity order = new OrderEntity();
            order.setUserId(userId);
            order.setPackageCategoryId(category.getId());
            order.setSenderName(trimToNull(dto.getSenderName()));
            order.setSenderPhone(trimToNull(dto.getSenderPhone()));
            order.setReceiverName(trimToNull(dto.getReceiverName()));
            order.setReceiverPhone(trimToNull(dto.getReceiverPhone()));
            String normalizedPickupAddress = resolveDisplayAddress(
                    dto.getPickupAddress(), dto.getPickupDoorNo(), dto.getPickupLandmark(), dto.getPickupLat(),
                    dto.getPickupLng());
            String normalizedDropAddress = resolveDisplayAddress(
                    dto.getDropAddress(), dto.getDropDoorNo(), dto.getDropLandmark(), dto.getDropLat(),
                    dto.getDropLng());
            order.setPickupAddress(normalizedPickupAddress);
            order.setPickupTag(trimToNull(dto.getPickupTag()));
            order.setPickupDoorNo(trimToNull(dto.getPickupDoorNo()));
            order.setPickupLandmark(trimToNull(dto.getPickupLandmark()));
            order.setDropAddress(normalizedDropAddress);
            order.setDropTag(trimToNull(dto.getDropTag()));
            order.setDropDoorNo(trimToNull(dto.getDropDoorNo()));
            order.setDropLandmark(trimToNull(dto.getDropLandmark()));
            order.setImageUrl(trimToNull(dto.getImageUrl()));
            applyParcelExtras(order, dto);
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
                    double platform = com.youdash.util.AppConfigPricing.incityPlatformFee(cfg);
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
                requireOutstationHubsMatchZones(
                        dto.getPickupLat(), dto.getPickupLng(),
                        dto.getDropLat(), dto.getDropLng(),
                        origin, dest);

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
                order.setDistanceKm(round4(nz(order.getPickupDistanceKm()) + nz(order.getHubDistanceKm())
                        + nz(order.getDropDistanceKm())));

                AppConfigEntity cfgOs = checkoutConfig;
                double routeRateOs = resolveRouteRate(dto.getOriginHubId(), dto.getDestinationHubId(), cfgOs);
                PricingService.OutstationBreakdown quoteLegs = pricingService.outstationBreakdown(
                        pickupDist, hubDist, dropDist, routeRateOs, dto.getWeight(), dtype, cfgOs);
                applyOutstationQuoteLegCosts(order, quoteLegs);

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
                    double baseOs = round2(quoteLegs.getTotal());
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
                    order.setSubtotal(quoteLegs.getSubtotal());
                    order.setGstAmount(quoteLegs.getGstAmount());
                    order.setPlatformFee(quoteLegs.getPlatformFee());
                    order.setCouponAmount(round2(couponDiscOs));
                    order.setTotalAmount(round2(baseOs - couponDiscOs));
                }
                // OUTSTATION stays admin-assigned; use BOOKED until admin assigns.
                order.setStatus(OrderStatus.BOOKED);
            }

            OrderEntity saved = orderRepository.save(order);
            saved.setDisplayOrderId("YP-" + saved.getId() + System.currentTimeMillis());
            if (saved.getPaymentType() == PaymentType.ONLINE
                    && (saved.getPaymentStatus() == null || saved.getPaymentStatus().isBlank())) {
                saved.setPaymentStatus("UNPAID");
            }
            saved = orderRepository.save(saved);
            applyOutstationOtpsOnCreate(saved);
            saved = orderRepository.save(saved);
            appendTimeline(saved, saved.getStatus(), "order_created", null, saved.getRiderId(), "Order created");
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
                        promo.normalizedCode() + " saved you ₹" + String.format("%.2f", promo.discountAmount())
                                + " on order " + saved.getId() + ".",
                        couponData,
                        NotificationType.USER_COUPON_APPLIED);
            }
            notifyAfterOrderCreated(saved);
            adminOrderTopicPublisher.publishOrderCreated(saved);
            if (saved.getServiceMode() == ServiceMode.INCITY && saved.getStatus() == OrderStatus.SEARCHING_RIDER) {
                dispatchService.dispatchNewIncityOrder(saved);
            }
            response.setData(toOrderDto(saved, null, null, false, null));
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
                    boolean riderAllowed = Objects.equals(o.getRiderId(), tokenUserId)
                            || Objects.equals(o.getPickupRiderId(), tokenUserId)
                            || Objects.equals(o.getDeliveryRiderId(), tokenUserId);
                    if (!riderAllowed) {
                        throw new RuntimeException("Access denied");
                    }
                } else if (!Objects.equals(o.getUserId(), tokenUserId)) {
                    throw new RuntimeException("Access denied");
                }
            }
            boolean riderOrderApi = "RIDER".equals(tokenType);
            OrderResponseDTO data = toOrderDto(o, null, null, riderOrderApi, riderOrderApi ? tokenUserId : null);
            Integer riderStars = riderRatingRepository.findByOrderId(o.getId())
                    .map(RiderRatingEntity::getStars)
                    .orElse(null);
            applyRiderRatingFlags(data, riderStars);
            if (riderOrderApi) {
                data = stripCommercialDetailsForRider(data);
                data.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(o, tokenUserId));
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
    public ApiResponse<List<OrderResponseDTO>> listRiderOrders(Long riderId, int page, int size, String date) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            int safePage = Math.max(0, page);
            int safeSize = Math.min(Math.max(1, size), 100);
            java.time.Instant startDate = null;
            java.time.Instant endDate = null;
            if (date != null && !date.isBlank()) {
                java.time.LocalDate ld = java.time.LocalDate.parse(date.trim());
                java.time.ZoneId zone = java.time.ZoneId.systemDefault();
                startDate = ld.atStartOfDay(zone).toInstant();
                endDate = ld.plusDays(1).atStartOfDay(zone).toInstant();
            }
            List<OrderEntity> orders = orderRepository.findRiderOrdersPaged(
                    riderId, startDate, endDate, PageRequest.of(safePage, safeSize));
            long totalCount = orderRepository.countRiderOrdersPaged(riderId, startDate, endDate);
            Set<Long> riderIds = orders.stream()
                    .flatMap(o -> java.util.stream.Stream.of(o.getRiderId(), o.getPickupRiderId(),
                            o.getDeliveryRiderId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            Map<Long, RiderEntity> riderMap = riderIds.isEmpty()
                    ? Map.of()
                    : riderRepository.findAllById(riderIds).stream()
                            .collect(Collectors.toMap(RiderEntity::getId, Function.identity()));
            Map<Long, VehicleEntity> vehicleMap = buildVehicleBatchForOrders(orders, riderMap);
            List<OrderResponseDTO> list = new ArrayList<>(orders.size());
            for (OrderEntity o : orders) {
                OrderResponseDTO d = stripCommercialDetailsForRider(toOrderDto(o, riderMap, vehicleMap, true, riderId));
                d.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(o, riderId));
                list.add(d);
            }
            response.setData(list);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            response.setTotalCount((int) totalCount);
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
            Map<Long, Integer> riderRatingByOrderId = buildRiderRatingByOrderId(orders);
            List<OrderResponseDTO> list = orders.stream()
                    .map(o -> toOrderDto(o, riderMap, vehicleMap, false, null))
                    .peek(d -> applyRiderRatingFlags(d, riderRatingByOrderId.get(d.getId())))
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
            Map<String, OrderAddressPreferenceEntity> prefByKey = orderAddressPreferenceRepository.findByUserId(userId)
                    .stream()
                    .collect(Collectors.toMap(
                            OrderAddressPreferenceEntity::getCoordinateKey,
                            Function.identity(),
                            (a, b) -> b));
            List<OrderAddressSuggestionDTO> suggestions = buildAddressSuggestions(orders, prefByKey, cap);
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

    @Override
    @Transactional
    public ApiResponse<String> editUserOrderAddressSuggestion(
            Long userId, Long tokenUserId, boolean admin, OrderAddressSuggestionEditRequestDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (!admin && !Objects.equals(userId, tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
            if (dto == null || dto.getRole() == null || dto.getLat() == null || dto.getLng() == null) {
                throw new RuntimeException("role, lat, lng are required");
            }
            String key = coordinateKey(dto.getRole(), dto.getLat(), dto.getLng());
            OrderAddressPreferenceEntity pref = orderAddressPreferenceRepository
                    .findByUserIdAndRoleAndCoordinateKey(userId, dto.getRole(), key)
                    .orElseGet(OrderAddressPreferenceEntity::new);
            pref.setUserId(userId);
            pref.setRole(dto.getRole());
            pref.setCoordinateKey(key);
            pref.setLat(dto.getLat());
            pref.setLng(dto.getLng());
            pref.setAddress(trimToNull(dto.getAddress()));
            pref.setTag(normalizeTag(dto.getTag()));
            pref.setDoorNo(trimToNull(dto.getDoorNo()));
            pref.setLandmark(trimToNull(dto.getLandmark()));
            pref.setContactName(trimToNull(dto.getContactName()));
            pref.setContactPhone(trimToNull(dto.getContactPhone()));
            pref.setIsHidden(false);
            orderAddressPreferenceRepository.save(pref);
            response.setData("OK");
            response.setMessage("Address suggestion updated");
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
    public ApiResponse<String> hideUserOrderAddressSuggestion(
            Long userId, Long tokenUserId, boolean admin, OrderAddressSuggestionHideRequestDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (!admin && !Objects.equals(userId, tokenUserId)) {
                throw new RuntimeException("Access denied");
            }
            if (dto == null || dto.getRole() == null || dto.getLat() == null || dto.getLng() == null) {
                throw new RuntimeException("role, lat, lng are required");
            }
            String key = coordinateKey(dto.getRole(), dto.getLat(), dto.getLng());
            OrderAddressPreferenceEntity pref = orderAddressPreferenceRepository
                    .findByUserIdAndRoleAndCoordinateKey(userId, dto.getRole(), key)
                    .orElseGet(OrderAddressPreferenceEntity::new);
            pref.setUserId(userId);
            pref.setRole(dto.getRole());
            pref.setCoordinateKey(key);
            pref.setLat(dto.getLat());
            pref.setLng(dto.getLng());
            pref.setIsHidden(true);
            orderAddressPreferenceRepository.save(pref);
            response.setData("OK");
            response.setMessage("Address suggestion hidden");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            setError(response, e.getMessage());
        }
        return response;
    }

    private static List<OrderAddressSuggestionDTO> buildAddressSuggestions(
            List<OrderEntity> orders,
            Map<String, OrderAddressPreferenceEntity> prefByKey,
            int maxSuggestions) {
        Set<String> seen = new HashSet<>();
        List<OrderAddressSuggestionDTO> out = new ArrayList<>();
        for (OrderEntity o : orders) {
            if (out.size() >= maxSuggestions) {
                break;
            }
            tryAddSuggestion(
                    out,
                    seen,
                    o,
                    OrderAddressRole.PICKUP,
                    o.getPickupLat(),
                    o.getPickupLng(),
                    o.getPickupAddress(),
                    o.getPickupTag(),
                    o.getPickupDoorNo(),
                    o.getPickupLandmark(),
                    o.getSenderName(),
                    o.getSenderPhone(),
                    prefByKey,
                    o.getCreatedAt());
            if (out.size() >= maxSuggestions) {
                break;
            }
            tryAddSuggestion(
                    out,
                    seen,
                    o,
                    OrderAddressRole.DROP,
                    o.getDropLat(),
                    o.getDropLng(),
                    o.getDropAddress(),
                    o.getDropTag(),
                    o.getDropDoorNo(),
                    o.getDropLandmark(),
                    o.getReceiverName(),
                    o.getReceiverPhone(),
                    prefByKey,
                    o.getCreatedAt());
        }
        out.sort(Comparator
                .comparing(OrderAddressSuggestionDTO::getLastUsedAt, Comparator.nullsLast(Comparator.naturalOrder()))
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
            String address,
            String tag,
            String doorNo,
            String landmark,
            String contactName,
            String contactPhone,
            Map<String, OrderAddressPreferenceEntity> prefByKey,
            Instant createdAt) {
        if (lat == null || lng == null) {
            return;
        }
        String key = coordinateKey(role, lat, lng);
        OrderAddressPreferenceEntity pref = prefByKey == null ? null : prefByKey.get(key);
        if (pref != null && Boolean.TRUE.equals(pref.getIsHidden())) {
            return;
        }
        if (!seen.add(key)) {
            return;
        }
        String resolvedAddress = overlay(pref != null ? pref.getAddress() : null, address);
        String resolvedTag = overlay(pref != null ? pref.getTag() : null, tag);
        String resolvedDoorNo = overlay(pref != null ? pref.getDoorNo() : null, doorNo);
        String resolvedLandmark = overlay(pref != null ? pref.getLandmark() : null, landmark);
        String resolvedContactName = overlay(pref != null ? pref.getContactName() : null, contactName);
        String resolvedContactPhone = overlay(pref != null ? pref.getContactPhone() : null, contactPhone);
        out.add(OrderAddressSuggestionDTO.builder()
                .role(role)
                .lat(lat)
                .lng(lng)
                .address(resolvedAddress)
                .tag(resolvedTag)
                .doorNo(resolvedDoorNo)
                .landmark(resolvedLandmark)
                .contactName(resolvedContactName)
                .contactPhone(resolvedContactPhone)
                .lastUsedAt(createdAt != null ? createdAt.toString() : null)
                .build());
    }

    @Override
    public ApiResponse<ManualOrderRequestResponseDTO> manualRequest(Long userId, ManualOrderRequestDTO dto) {
        ApiResponse<ManualOrderRequestResponseDTO> response = new ApiResponse<>();
        try {
            validateCoordsWeight(dto.getPickupLat(), dto.getPickupLng(), dto.getDropLat(), dto.getDropLng(),
                    dto.getWeight());
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
                    .map(o -> toOrderDto(o, riderMap, vehicleMap, false, null))
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
        return adminAssignRiders(orderId, riderId, riderId);
    }

    @Override
    public ApiResponse<OrderResponseDTO> adminAssignRiders(
            Long orderId,
            Long pickupRiderId,
            Long deliveryRiderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity o = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (pickupRiderId == null && deliveryRiderId == null) {
                throw new RuntimeException("pickupRiderId or deliveryRiderId is required");
            }
            assertOnlinePaidBeforeRiderAssignment(o);
            if (pickupRiderId != null
                    && o.getServiceMode() == ServiceMode.OUTSTATION
                    && OutstationCodPolicy.isHubToDoor(o)) {
                throw new RuntimeException("Hub-to-door orders do not use a pickup rider");
            }
            if (pickupRiderId != null
                    && deliveryRiderId != null
                    && Objects.equals(pickupRiderId, deliveryRiderId)
                    && o.getServiceMode() == ServiceMode.OUTSTATION
                    && isOutstationDoorDeliveryType(o.getDeliveryType())) {
                throw new RuntimeException(
                        "Pickup and delivery riders must be different for door-to-door orders");
            }
            if (deliveryRiderId != null
                    && pickupRiderId == null
                    && "DOOR_TO_HUB".equalsIgnoreCase(String.valueOf(o.getDeliveryType()))) {
                throw new RuntimeException(
                        "Door-to-hub orders use hub collection only; assign a pickup rider or verify hub collection instead of a delivery rider");
            }
            if (deliveryRiderId != null
                    && o.getServiceMode() == ServiceMode.OUTSTATION
                    && isOutstationDoorDeliveryType(o.getDeliveryType())
                    && !isOutstationDestinationDeliveryPhase(o.getStatus())) {
                throw new RuntimeException(
                        "Delivery rider can only be assigned when the parcel is at the destination hub");
            }

            Long primaryRiderId = deliveryRiderId != null ? deliveryRiderId : pickupRiderId;
            if (pickupRiderId != null) {
                RiderEntity pickupRider = riderRepository.findById(pickupRiderId)
                        .orElseThrow(() -> new RuntimeException("Pickup rider not found"));
                if (!RiderApprovalStatus.APPROVED.equals(pickupRider.getApprovalStatus())) {
                    throw new RuntimeException("Pickup rider is not approved");
                }
                if (riderWalletService.isRiderDispatchBlocked(pickupRiderId)) {
                    throw new RuntimeException("Pickup rider must deposit COD commission at hub before new orders");
                }
                o.setPickupRiderId(pickupRiderId);
            }
            if (deliveryRiderId != null) {
                RiderEntity deliveryRider = riderRepository.findById(deliveryRiderId)
                        .orElseThrow(() -> new RuntimeException("Delivery rider not found"));
                if (!RiderApprovalStatus.APPROVED.equals(deliveryRider.getApprovalStatus())) {
                    throw new RuntimeException("Delivery rider is not approved");
                }
                if (riderWalletService.isRiderDispatchBlocked(deliveryRiderId)) {
                    throw new RuntimeException("Delivery rider must deposit COD commission at hub before new orders");
                }
                o.setDeliveryRiderId(deliveryRiderId);
            }
            o.setRiderId(primaryRiderId);
            OrderStatus assignTargetStatus = resolveStatusAfterAdminRiderAssign(o, pickupRiderId, deliveryRiderId);
            transitionStatus(o, assignTargetStatus);
            final boolean deliveryOnlyAssign = deliveryRiderId != null && pickupRiderId == null;
            final boolean pickupOnlyAssign = pickupRiderId != null && deliveryRiderId == null;
            if (deliveryOnlyAssign || (deliveryRiderId != null && !Objects.equals(deliveryRiderId, pickupRiderId))) {
                o.setDeliveryOtp(DeliveryOtpGenerator.generate());
                o.setDeliveryOtpGeneratedAt(Instant.now());
                o.setIsOtpVerified(false);
                o.setDeliveryOtpAttempts(0);
            }
            if (pickupOnlyAssign
                    && o.getServiceMode() == ServiceMode.OUTSTATION
                    && !OutstationCodPolicy.isHubToDoor(o)) {
                o.setPickupOtp(DeliveryOtpGenerator.generate());
            }
            OrderEntity saved = orderRepository.save(o);

            final String publishedStatus = saved.getStatus().name();
            final boolean pickupAssign = pickupRiderId != null;
            final boolean splitLegAssign = deliveryRiderId != null
                    && pickupRiderId != null
                    && !Objects.equals(deliveryRiderId, pickupRiderId);

            if (pickupAssign && !deliveryOnlyAssign) {
                appendTimeline(
                        saved,
                        OrderStatus.PICKUP_ASSIGNED,
                        "pickup_rider_assigned",
                        saved.getOriginHubId(),
                        pickupRiderId,
                        "Pickup rider assigned by admin");
            }
            if (deliveryOnlyAssign || splitLegAssign) {
                appendTimeline(
                        saved,
                        OrderStatus.OUT_FOR_DELIVERY,
                        "delivery_rider_assigned",
                        saved.getDestinationHubId(),
                        deliveryRiderId,
                        "Delivery rider assigned by admin");
            } else if (pickupAssign && deliveryRiderId != null && Objects.equals(pickupRiderId, deliveryRiderId)) {
                appendTimeline(
                        saved,
                        saved.getStatus(),
                        "rider_assigned",
                        saved.getOriginHubId(),
                        primaryRiderId,
                        "Rider assigned by admin");
            }

            try {
                if (pickupRiderId != null && !deliveryOnlyAssign) {
                    notificationService.sendToRider(
                            pickupRiderId,
                            "New pickup assigned",
                            "Order #" + saved.getId() + " assigned for pickup stage.",
                            NotificationService.baseData(saved.getId(), OrderStatus.PICKUP_ASSIGNED.name(),
                                    NotificationType.RIDER_JOB_ASSIGNED),
                            NotificationType.RIDER_JOB_ASSIGNED);
                }
                if (deliveryRiderId != null && !Objects.equals(deliveryRiderId, pickupRiderId)) {
                    notificationService.sendToRider(
                            deliveryRiderId,
                            "New delivery assigned",
                            "Order #" + saved.getId() + " assigned for delivery stage.",
                            NotificationService.baseData(saved.getId(), OrderStatus.OUT_FOR_DELIVERY.name(),
                                    NotificationType.RIDER_JOB_ASSIGNED),
                            NotificationType.RIDER_JOB_ASSIGNED);
                }
            } catch (Exception ignored) {
                // Keep assignment successful even if one notification channel fails.
            }
            try {
                String userTitle = deliveryOnlyAssign ? "Delivery rider assigned" : "Rider assigned";
                String userBody = deliveryOnlyAssign
                        ? "A delivery rider has been assigned to order #" + saved.getId() + "."
                        : "A rider has been assigned to order #" + saved.getId() + ".";
                notificationService.sendToUser(
                        saved.getUserId(),
                        userTitle,
                        userBody,
                        NotificationService.baseData(saved.getId(), publishedStatus,
                                NotificationType.RIDER_ASSIGNED),
                        NotificationType.RIDER_ASSIGNED);
            } catch (Exception ignored) {
                // Keep assignment successful even if one notification channel fails.
            }
            try {
                UserOrderEventDTO userEvt = new UserOrderEventDTO();
                userEvt.setOrderId(saved.getId());
                userEvt.setEvent(deliveryOnlyAssign ? "delivery_rider_assigned" : "rider_found");
                userEvt.setEventType(deliveryOnlyAssign ? "delivery_rider_assigned" : "rider_assigned");
                userEvt.setEventVersion(1);
                userEvt.setTsEpochMs(Instant.now().toEpochMilli());
                userEvt.setSource("backend");
                userEvt.setStatus(publishedStatus);
                userEvt.setServiceMode(saved.getServiceMode() == null ? null : saved.getServiceMode().name());
                userEvt.setRiderId(primaryRiderId);
                messagingTemplate.convertAndSend("/topic/users/" + saved.getUserId() + "/order-events", userEvt);
            } catch (Exception ignored) {
                // Keep assignment successful even if one notification channel fails.
            }
            adminOrderTopicPublisher.publishRiderAssigned(saved);
            try {
                userActiveOrderTopicPublisher.publishStatusUpdated(
                        saved.getUserId(),
                        saved.getId(),
                        publishedStatus,
                        saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                        primaryRiderId);
                userActiveOrderTopicPublisher.publishSnapshot(
                        saved.getUserId(),
                        saved.getId(),
                        publishedStatus,
                        saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                        primaryRiderId,
                        null,
                        null);
            } catch (Exception ignored) {
                // Keep assignment successful even if one notification channel fails.
            }
            try {
                ServiceMode mode = saved.getServiceMode();
                Double codAmount = saved.getPaymentType() == PaymentType.COD ? saved.getTotalAmount() : null;
                if (pickupRiderId != null && !deliveryOnlyAssign) {
                    riderActiveOrderTopicPublisher.publish(
                            pickupRiderId,
                            saved.getId(),
                            OrderStatus.PICKUP_ASSIGNED,
                            "assigned",
                            null,
                            codAmount,
                            mode);
                    riderActiveOrderTopicPublisher.publishSnapshot(
                            pickupRiderId,
                            saved.getId(),
                            OrderStatus.PICKUP_ASSIGNED,
                            mode);
                }
                if (deliveryRiderId != null && !Objects.equals(deliveryRiderId, pickupRiderId)) {
                    riderActiveOrderTopicPublisher.publish(
                            deliveryRiderId,
                            saved.getId(),
                            OrderStatus.OUT_FOR_DELIVERY,
                            "assigned",
                            null,
                            codAmount,
                            mode);
                    riderActiveOrderTopicPublisher.publishSnapshot(
                            deliveryRiderId,
                            saved.getId(),
                            OrderStatus.OUT_FOR_DELIVERY,
                            mode);
                }
            } catch (Exception ignored) {
                // Keep assignment successful even if one notification channel fails.
            }
            response.setData(toOrderDto(saved, null, null, false, null));
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
    public ApiResponse<OrderResponseDTO> adminUpdateStatus(
            Long orderId, OrderStatus status, String otp, boolean adminOverride, String codCollectionMode) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        OrderEntity o = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        OrderStatus target = normalizeAdminTargetStatus(o, status);
        validateAdminOutstationStatusUpdate(o, target, codCollectionMode, adminOverride);
        if (target == OrderStatus.DELIVERED && o.getPaymentType() == PaymentType.COD) {
            if (o.getCodCollectionMode() == null || o.getCodCollectedAmount() == null) {
                throw new RuntimeException("COD details missing. Use /order/complete");
            }
        }
        applyAdminOutstationOtpGates(o, target, otp, adminOverride);
        transitionStatus(o, target);
        if (target == OrderStatus.OUT_FOR_DELIVERY
                || (target == OrderStatus.AWAITING_HUB_COLLECTION && OutstationCodPolicy.isDoorToHub(o))) {
            o.setDeliveryOtp(DeliveryOtpGenerator.generate());
            o.setDeliveryOtpGeneratedAt(Instant.now());
            o.setIsOtpVerified(false);
            o.setDeliveryOtpAttempts(0);
        }
        if (target == OrderStatus.PICKED_UP) {
            o.setPickupOtp(null);
        }
        if (target == OrderStatus.DELIVERED) {
            o.setDeliveryOtp(null);
            o.setIsOtpVerified(true);
        }
        OrderEntity saved = orderRepository.save(o);
        String timelineNote = adminOverride ? "Admin status update (OTP override)" : "Admin status update";
        appendTimeline(saved, target, "status_updated", saved.getDestinationHubId(), saved.getRiderId(), timelineNote);
        publishAdminStatusEvent(saved, target);
        response.setData(toOrderDto(saved, null, null, false, null));
        response.setMessage("Status updated");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> adminVerifyHubHandover(Long orderId, VerifyHubHandoverRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (orderId == null) {
            throw new RuntimeException("orderId is required");
        }
        if (dto == null || dto.getType() == null || dto.getType().isBlank()) {
            throw new RuntimeException("type is required (DROP or COLLECT)");
        }
        OrderEntity o = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (o.getServiceMode() != ServiceMode.OUTSTATION) {
            throw new RuntimeException("Hub handover is only for OUTSTATION orders");
        }
        boolean adminOverride = Boolean.TRUE.equals(dto.getAdminOverride());
        OutstationHubHandover.Type handoverType = OutstationHubHandover.Type.parse(dto.getType());
        String providedOtp = dto.getOtp() == null ? "" : dto.getOtp().trim();
        OrderStatus targetStatus;
        String timelineNote;

        if (handoverType == OutstationHubHandover.Type.DROP) {
            if (!OutstationHubHandover.canDropAtOriginHub(o)) {
                throw new RuntimeException("Hub drop is only allowed for HUB_TO_DOOR orders in BOOKED status");
            }
            String dropCode = o.getPickupOtp();
            if (!adminOverride) {
                if (dropCode == null || dropCode.isBlank()) {
                    throw new RuntimeException("No drop-off OTP on this order");
                }
                if (providedOtp.isEmpty() || !dropCode.equals(providedOtp)) {
                    throw new RuntimeException("Invalid drop-off OTP");
                }
            }
            recordSenderCodAtHubIfNeeded(o, dto, adminOverride);
            o.setPickupOtp(null);
            targetStatus = OrderStatus.AT_ORIGIN_HUB;
            timelineNote = adminOverride ? "Hub drop confirmed (OTP override)" : "Hub drop confirmed";
        } else {
            if (!OutstationHubHandover.canCollectAtDestinationHub(o)) {
                throw new RuntimeException(
                        "Hub collection is only allowed for DOOR_TO_HUB orders in AWAITING_HUB_COLLECTION status");
            }
            if (o.getDeliveryOtp() == null || o.getDeliveryOtp().isBlank()) {
                o.setDeliveryOtp(DeliveryOtpGenerator.generate());
                o.setDeliveryOtpGeneratedAt(Instant.now());
            }
            if (!adminOverride) {
                if (!o.getDeliveryOtp().equals(providedOtp)) {
                    throw new RuntimeException("Invalid collection OTP");
                }
            }
            o.setDeliveryOtp(null);
            o.setIsOtpVerified(true);
            targetStatus = OrderStatus.COLLECTED;
            timelineNote = adminOverride ? "Hub collection confirmed (OTP override)" : "Hub collection confirmed";
        }

        transitionStatus(o, targetStatus);
        OrderEntity saved = orderRepository.save(o);
        appendTimeline(saved, targetStatus, "hub_handover_" + handoverType.name().toLowerCase(),
                handoverType == OutstationHubHandover.Type.DROP ? saved.getOriginHubId() : saved.getDestinationHubId(),
                null, timelineNote);
        publishAdminStatusEvent(saved, targetStatus);
        response.setData(toOrderDto(saved, null, null, false, null));
        response.setMessage("Hub handover recorded");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<OrderResponseDTO> completeOrderForRider(Long tokenUserId, String tokenType,
            OrderCompleteRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (!"RIDER".equals(tokenType)) {
            throw new BadRequestException("Rider token required");
        }
        if (dto == null || dto.getOrderId() == null || dto.getOrderId().isBlank()) {
            throw new BadRequestException("orderId is required");
        }
        Long actingRiderId = riderAccessVerifier.resolveActingRiderIdFromToken(tokenUserId, tokenType);

        OrderEntity o = resolveOrderByIdOrReference(dto.getOrderId().trim());
        boolean canCompleteDelivery = Objects.equals(o.getRiderId(), actingRiderId)
                || Objects.equals(o.getDeliveryRiderId(), actingRiderId);
        if (o.getRiderId() == null && o.getDeliveryRiderId() == null) {
            throw new BadRequestException("Access denied");
        }
        if (!canCompleteDelivery) {
            throw new BadRequestException("Access denied");
        }

        boolean financialExists = riderWalletService.isOrderWalletSettlementComplete(o);
        boolean alreadyDelivered = o.getStatus() == OrderStatus.DELIVERED;

        if (alreadyDelivered && financialExists) {
            OrderEntity refreshed = orderRepository.findById(o.getId()).orElse(o);
            markRiderAvailableAfterDelivery(refreshed.getRiderId());
            OrderResponseDTO earlyDto = stripCommercialDetailsForRider(toOrderDto(refreshed, null, null, true, actingRiderId));
            earlyDto.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(refreshed, actingRiderId));
            response.setData(earlyDto);
            response.setMessage("Order completed");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
            return response;
        }

        if (!alreadyDelivered) {
            boolean atDeliveryCompletion = o.getStatus() == OrderStatus.IN_TRANSIT
                    || o.getStatus() == OrderStatus.OUT_FOR_DELIVERY;
            if (!atDeliveryCompletion) {
                throw new BadRequestException("Order cannot be completed from status: " + o.getStatus());
            }
            if (!Boolean.TRUE.equals(o.getIsOtpVerified())) {
                throw new BadRequestException("Delivery OTP not verified");
            }
        }

        if (o.getPaymentType() == null) {
            throw new BadRequestException("Order payment type is missing");
        }

        if (o.getPaymentType() == PaymentType.ONLINE) {
            // Amount was settled at payment gateway; order row holds totalAmount + PAID —
            // body only needs orderId.
            if (!alreadyDelivered && !isOrderPaidOnline(o)) {
                throw new BadRequestException("Online order must be paid before marking delivered");
            }
            o.setCodCollectedAmount(null);
            o.setCodCollectionMode(null);
            o.setCodSettlementStatus(CodSettlementStatus.SETTLED);
        } else if (o.getPaymentType() == PaymentType.COD) {
            if (OutstationCodPolicy.codCollectedAtPickupLeg(o)) {
                // D2D/D2H: pickup rider collected from sender; delivery rider only verifies OTP.
                if (!alreadyDelivered && nz(o.getCodCollectedAmount()) <= 0.0) {
                    throw new BadRequestException(
                            "COD must be collected from the sender at pickup before completing delivery");
                }
                if (o.getCodCollectionMode() == null && nz(o.getCodCollectedAmount()) > 0.0) {
                    o.setCodCollectionMode(CodCollectionMode.CASH);
                }
            } else if (o.getCodCollectionMode() == null || o.getCodCollectedAmount() == null) {
                if (dto.getCodCollectionMode() == null || dto.getCodCollectionMode().isBlank()) {
                    throw new BadRequestException("codCollectionMode is required for COD (CASH or QR)");
                }
                CodCollectionMode mode = CodCollectionMode.parseClientValue(dto.getCodCollectionMode());
                double collected = round2(nz(o.getTotalAmount()));
                if (collected <= 0) {
                    throw new BadRequestException("Invalid order total for COD settlement");
                }
                o.setCodCollectionMode(mode);
                o.setCodCollectedAmount(collected);
            }
            o.setCodSettlementStatus(CodSettlementStatus.PENDING);
        } else {
            throw new BadRequestException("Unsupported payment type: " + o.getPaymentType());
        }

        if (!alreadyDelivered) {
            transitionStatus(o, OrderStatus.DELIVERED);
        }
        OrderEntity saved = orderRepository.save(o);
        appendTimeline(saved, OrderStatus.DELIVERED, "order_completed", saved.getDestinationHubId(), actingRiderId,
                "Delivery completed");

        try {
            riderWalletService.settleOrderDelivered(
                    saved,
                    saved.getPaymentType() == PaymentType.COD ? saved.getCodCollectionMode() : null,
                    saved.getCodCollectedAmount(),
                    actingRiderId,
                    "RIDER");
        } catch (RuntimeException ex) {
            // Keep DELIVERED even if wallet settlement fails (e.g. split-leg race); ops can retry settlement.
        }

        UserOrderEventDTO deliveredEvt = new UserOrderEventDTO();
        deliveredEvt.setOrderId(saved.getId());
        deliveredEvt.setEvent("delivered");
        deliveredEvt.setEventType("status_updated");
        deliveredEvt.setEventVersion(1);
        deliveredEvt.setTsEpochMs(Instant.now().toEpochMilli());
        deliveredEvt.setSource("backend");
        deliveredEvt.setStatus(OrderStatus.DELIVERED.name());
        deliveredEvt.setServiceMode(saved.getServiceMode() == null ? null : saved.getServiceMode().name());
        deliveredEvt.setRiderId(saved.getRiderId());
        deliveredEvt.setCanRateRider(Boolean.TRUE);
        deliveredEvt.setRiderRatingSubmitted(Boolean.FALSE);
        deliveredEvt.setRiderRating(null);
        messagingTemplate.convertAndSend("/topic/users/" + saved.getUserId() + "/order-events", deliveredEvt);
        adminOrderTopicPublisher.publishStatusUpdated(saved);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(),
                saved.getId(),
                OrderStatus.DELIVERED.name(),
                saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                saved.getRiderId());
        userActiveOrderTopicPublisher.publishReleased(saved.getUserId(), saved.getId());

        sendMilestoneUserStatusPush(saved, OrderStatus.DELIVERED);

        markRiderAvailableAfterDelivery(actingRiderId);

        riderActiveOrderTopicPublisher.publish(actingRiderId, saved.getId(), OrderStatus.DELIVERED, "delivered");

        OrderEntity refreshed = orderRepository.findById(saved.getId()).orElse(saved);
        OrderResponseDTO completedDto = stripCommercialDetailsForRider(toOrderDto(refreshed, null, null, true, actingRiderId));
        completedDto.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(refreshed, actingRiderId));
        response.setData(completedDto);
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

        Long actingRiderId = null;
        boolean allowed = false;
        if ("USER".equals(tokenType) && Objects.equals(o.getUserId(), tokenUserId)) {
            allowed = true;
        } else if ("RIDER".equals(tokenType)) {
            actingRiderId = riderAccessVerifier.resolveActingRiderIdFromToken(tokenUserId, tokenType);
            allowed = Objects.equals(o.getRiderId(), actingRiderId)
                    || Objects.equals(o.getDeliveryRiderId(), actingRiderId);
        }
        if (!allowed) {
            throw new BadRequestException("Access denied");
        }

        if (o.getStatus() != OrderStatus.IN_TRANSIT) {
            if (o.getStatus() != OrderStatus.OUT_FOR_DELIVERY) {
                throw new BadRequestException("OTP can only be verified while order is IN_TRANSIT or OUT_FOR_DELIVERY");
            }
        }
        if (o.getDeliveryOtp() == null) {
            throw new BadRequestException("No delivery OTP on this order");
        }
        String provided = dto.getOtp().trim();
        if (!o.getDeliveryOtp().equals(provided)) {
            throw new BadRequestException("Invalid OTP");
        }
        o.setIsOtpVerified(true);
        o.setDeliveryOtpAttempts((o.getDeliveryOtpAttempts() == null ? 0 : o.getDeliveryOtpAttempts()) + 1);
        OrderEntity saved = orderRepository.save(o);
        final String publishedStatus = saved.getStatus().name();
        Long eventRiderId = saved.getDeliveryRiderId() != null ? saved.getDeliveryRiderId() : saved.getRiderId();
        Long timelineRiderId = actingRiderId != null ? actingRiderId : eventRiderId;
        appendTimeline(saved, saved.getStatus(), "otp_verified", saved.getDestinationHubId(), timelineRiderId,
                "Delivery OTP verified");

        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(saved.getId());
        evt.setEvent("otp_verified");
        evt.setEventType("otp_verified");
        evt.setEventVersion(1);
        evt.setTsEpochMs(Instant.now().toEpochMilli());
        evt.setSource("backend");
        evt.setStatus(publishedStatus);
        evt.setServiceMode(saved.getServiceMode() == null ? null : saved.getServiceMode().name());
        evt.setRiderId(eventRiderId);
        evt.setCanRateRider(Boolean.FALSE);
        evt.setRiderRatingSubmitted(Boolean.FALSE);
        evt.setRiderRating(null);
        messagingTemplate.convertAndSend("/topic/users/" + saved.getUserId() + "/order-events", evt);
        adminOrderTopicPublisher.publish("otp_verified", saved);
        userActiveOrderTopicPublisher.publishStatusUpdated(
                saved.getUserId(),
                saved.getId(),
                publishedStatus,
                saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                eventRiderId);

        if (eventRiderId != null) {
            riderActiveOrderTopicPublisher.publish(
                    eventRiderId, saved.getId(), saved.getStatus(), "otp_verified");
        }

        if ("RIDER".equals(tokenType) && saved.getUserId() != null) {
            Map<String, String> userOtpData = new HashMap<>(
                    NotificationService.baseData(
                            saved.getId(),
                            publishedStatus,
                            NotificationType.USER_OTP_VERIFIED_BY_RIDER));
            if (eventRiderId != null) {
                userOtpData.put("riderId", String.valueOf(eventRiderId));
            }
            notificationService.sendToUser(
                    saved.getUserId(),
                    "Delivery OTP verified",
                    "Rider verified the OTP for order #" + saved.getId() + ".",
                    userOtpData,
                    NotificationType.USER_OTP_VERIFIED_BY_RIDER);
        } else if ("USER".equals(tokenType) && eventRiderId != null) {
            Map<String, String> riderOtpData = new HashMap<>(
                    NotificationService.baseData(
                            saved.getId(),
                            publishedStatus,
                            NotificationType.RIDER_OTP_VERIFIED_BY_USER));
            notificationService.sendToRider(
                    eventRiderId,
                    "Delivery OTP verified",
                    "Customer verified the OTP for order #" + saved.getId() + ". You can complete delivery.",
                    riderOtpData,
                    NotificationType.RIDER_OTP_VERIFIED_BY_USER);
        }

        boolean riderApi = "RIDER".equals(tokenType);
        OrderResponseDTO data = toOrderDto(saved, null, null, riderApi, riderApi ? actingRiderId : null);
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
    public ApiResponse<OrderResponseDTO> resendDeliveryOtp(Long orderId, Long tokenUserId, String tokenType) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        if (orderId == null) {
            throw new BadRequestException("orderId is required");
        }
        OrderEntity order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BadRequestException("Order not found"));
        boolean allowed = false;
        if ("USER".equals(tokenType) && Objects.equals(order.getUserId(), tokenUserId)) {
            allowed = true;
        } else if ("RIDER".equals(tokenType)) {
            Long actingRiderId = riderAccessVerifier.resolveActingRiderIdFromToken(tokenUserId, tokenType);
            Long assigned = order.getDeliveryRiderId() != null ? order.getDeliveryRiderId() : order.getRiderId();
            allowed = Objects.equals(assigned, actingRiderId);
        } else if ("ADMIN".equals(tokenType)) {
            allowed = true;
        }
        if (!allowed) {
            throw new BadRequestException("Access denied");
        }
        if (order.getStatus() != OrderStatus.OUT_FOR_DELIVERY && order.getStatus() != OrderStatus.IN_TRANSIT) {
            throw new BadRequestException("OTP resend is only allowed while delivery is in progress");
        }

        order.setDeliveryOtp(DeliveryOtpGenerator.generate());
        order.setDeliveryOtpGeneratedAt(Instant.now());
        order.setIsOtpVerified(false);
        order = orderRepository.save(order);
        appendTimeline(order, order.getStatus(), "otp_resent", order.getDestinationHubId(), order.getRiderId(),
                "Delivery OTP regenerated");

        response.setData(toOrderDto(order, null, null, "RIDER".equals(tokenType),
                "RIDER".equals(tokenType) ? tokenUserId : null));
        response.setMessage("Delivery OTP resent");
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
            response.setData(toOrderDto(o, null, null, false, null));
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
            userActiveOrderTopicPublisher.publishReleased(o.getUserId(), o.getId());
            // Match payment-timeout path: user tracking + banner also listen on
            // order-events.
            sendUserOrderCancelledSocketEvent(o.getUserId(), o.getId());
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
        response.setData(toOrderDto(refreshed, null, null, false, null));
        response.setMessage("Order cancelled");
        response.setMessageKey("SUCCESS");
        response.setSuccess(true);
        response.setStatus(200);
        return response;
    }

    private void sendUserOrderCancelledSocketEvent(Long userId, Long orderId) {
        if (userId == null || orderId == null) {
            return;
        }
        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(orderId);
        evt.setEvent("cancelled");
        evt.setEventType("cancelled");
        evt.setEventVersion(1);
        evt.setTsEpochMs(Instant.now().toEpochMilli());
        evt.setSource("backend");
        evt.setStatus(OrderStatus.CANCELLED.name());
        evt.setServiceMode(ServiceMode.INCITY.name());
        evt.setPaymentDueAtEpochMs(null);
        evt.setRiderId(null);
        messagingTemplate.convertAndSend("/topic/users/" + userId + "/order-events", evt);
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
                    NotificationService.baseData(saved.getId(), OrderStatus.BOOKED.name(),
                            NotificationType.ADMIN_OUTSTATION_PENDING_ASSIGNMENT),
                    NotificationType.ADMIN_OUTSTATION_PENDING_ASSIGNMENT);
        }
        // INCITY: dispatch service handles rider notifications (socket/push). No
        // auto-assign here.
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

    private void sendMilestoneUserStatusPush(OrderEntity order, OrderStatus status) {
        if (order == null || order.getUserId() == null || order.getId() == null || status == null) {
            return;
        }
        NotificationType type = switch (status) {
            case IN_TRANSIT -> NotificationType.IN_TRANSIT_TO_DEST_HUB;
            case OUT_FOR_DELIVERY -> NotificationType.OUT_FOR_DELIVERY;
            case DELIVERED -> NotificationType.DELIVERED;
            default -> null;
        };
        if (type == null) {
            return;
        }
        String title = "Order update";
        String body = switch (status) {
            case IN_TRANSIT -> "Order #" + order.getId() + " is in transit";
            case OUT_FOR_DELIVERY -> "Order #" + order.getId() + " is out for delivery";
            case DELIVERED -> "Order #" + order.getId() + " was delivered";
            default -> null;
        };
        if (body == null) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                title,
                body,
                NotificationService.baseData(order.getId(), status.name(), type),
                type);
    }

    private AppConfigEntity requireConfig() {
        return appConfigRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException(
                        "Global config missing — ensure youdash_price_config row id=1 exists"));
    }

    private double resolveRouteRate(Long originHubId, Long destHubId, AppConfigEntity config) {
        return routeRateResolver.resolveHubLegRatePerKm(originHubId, destHubId, config);
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

    private void validateCoordsWeight(Double pickupLat, Double pickupLng, Double dropLat, Double dropLng,
            Double weight) {
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

    private static void applyParcelExtras(OrderEntity order, CreateOrderRequestDTO dto) {
        order.setPackageContents(trimToNull(dto.getPackageContents()));
        order.setDeclaredValue(dto.getDeclaredValue());
        Integer pieces = dto.getPieceCount();
        order.setPieceCount(pieces != null && pieces > 0 ? pieces : 1);
        order.setIsFragile(Boolean.TRUE.equals(dto.getIsFragile()));
        order.setContainsLiquid(Boolean.TRUE.equals(dto.getContainsLiquid()));
        order.setContainsBattery(Boolean.TRUE.equals(dto.getContainsBattery()));
        order.setProhibitedItemsAccepted(Boolean.TRUE.equals(dto.getProhibitedItemsAccepted()));
        order.setParcelDeclarationAccepted(Boolean.TRUE.equals(dto.getParcelDeclarationAccepted()));
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String resolveDisplayAddress(
            String primaryAddress,
            String doorNo,
            String landmark,
            Double lat,
            Double lng) {
        String primary = trimToNull(primaryAddress);
        if (primary != null) {
            return primary;
        }
        String door = trimToNull(doorNo);
        String mark = trimToNull(landmark);
        if (door != null && mark != null) {
            return door + ", " + mark;
        }
        if (door != null) {
            return door;
        }
        if (mark != null) {
            return mark;
        }
        if (lat != null && lng != null) {
            return String.format(Locale.ROOT, "%.6f, %.6f", lat, lng);
        }
        return null;
    }

    private static String normalizeTag(String tag) {
        String t = trimToNull(tag);
        return t == null ? null : t.toUpperCase(Locale.ROOT);
    }

    private static String overlay(String preferred, String fallback) {
        String p = trimToNull(preferred);
        return p != null ? p : trimToNull(fallback);
    }

    private static String coordinateKey(OrderAddressRole role, Double lat, Double lng) {
        return role.name() + '|' + String.format(Locale.ROOT, "%.5f,%.5f", lat, lng);
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

    private Map<Long, VehicleEntity> buildVehicleBatchForOrders(List<OrderEntity> orders,
            Map<Long, RiderEntity> riderMap) {
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

    /**
     * Rider's catalog vehicle when assigned; otherwise the order's booked vehicle
     * (INCITY).
     */
    private static Long resolveEffectiveVehicleId(OrderEntity o, RiderEntity rider) {
        if (rider != null && rider.getVehicleId() != null) {
            return rider.getVehicleId();
        }
        return o.getVehicleId();
    }

    OrderResponseDTO toOrderDto(OrderEntity o) {
        return toOrderDto(o, null, null, false, null);
    }

    /**
     * Full mapping for rider apps: narrower OTP rules + strips GST/platform fields.
     */
    OrderResponseDTO toOrderDtoForRider(OrderEntity o) {
        Long viewer = o != null ? o.getRiderId() : null;
        OrderResponseDTO dto = stripCommercialDetailsForRider(toOrderDto(o, null, null, true, viewer));
        if (dto != null && o != null && viewer != null) {
            dto.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(o, viewer));
        }
        return dto;
    }

    /**
     * Full mapping for rider apps with explicit viewer rider context for OUTSTATION
     * hub masking.
     */
    OrderResponseDTO toOrderDtoForRider(OrderEntity o, Long viewerRiderId) {
        OrderResponseDTO dto = stripCommercialDetailsForRider(toOrderDto(o, null, null, true, viewerRiderId));
        if (dto != null && o != null && viewerRiderId != null) {
            dto.setEarnedAmount(riderWalletService.resolveRiderEarningForOrder(o, viewerRiderId));
        }
        return dto;
    }

    /**
     * @param riderBatch    optional map of rider id → entity for batch APIs;
     *                      {@code null} loads rider individually.
     * @param vehicleBatch  optional map of vehicle id → entity; {@code null} loads
     *                      vehicle individually when needed.
     * @param riderOrderApi {@code true} for rider-facing endpoints (OTP only in
     *                      {@code IN_TRANSIT} until verified).
     *                      {@code false} for customer/admin — OTP shown from
     *                      accept/payment through trip until verified.
     */
    OrderResponseDTO toOrderDto(OrderEntity o, Map<Long, RiderEntity> riderBatch, Map<Long, VehicleEntity> vehicleBatch,
            boolean riderOrderApi, Long viewerRiderId) {
        RiderEntity rider = resolveRiderForOrderDto(resolveDisplayRiderId(o, riderOrderApi), riderBatch);
        String riderName = rider != null ? rider.getName() : null;
        String riderPhone = rider != null ? rider.getPhone() : null;
        String deliveryOtp = resolveDeliveryOtpForResponse(o, riderOrderApi);
        String pickupOtp = resolvePickupOtpForResponse(o, riderOrderApi);
        String hubCollectionOtp = resolveHubCollectionOtpForResponse(o, riderOrderApi);
        HubEntity originHub = resolveHubById(o.getOriginHubId());
        HubEntity destinationHub = resolveHubById(o.getDestinationHubId());
        String resolvedPickupAddress = resolveDisplayAddress(
                o.getPickupAddress(), o.getPickupDoorNo(), o.getPickupLandmark(), o.getPickupLat(), o.getPickupLng());
        String resolvedDropAddress = resolveDisplayAddress(
                o.getDropAddress(), o.getDropDoorNo(), o.getDropLandmark(), o.getDropLat(), o.getDropLng());

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

        OrderResponseDTO dto = OrderResponseDTO.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .categoryId(o.getPackageCategoryId())
                .senderName(o.getSenderName())
                .senderPhone(o.getSenderPhone())
                .receiverName(o.getReceiverName())
                .receiverPhone(o.getReceiverPhone())
                .pickupAddress(resolvedPickupAddress)
                .pickupTag(o.getPickupTag())
                .pickupDoorNo(o.getPickupDoorNo())
                .pickupLandmark(o.getPickupLandmark())
                .dropAddress(resolvedDropAddress)
                .dropTag(o.getDropTag())
                .dropDoorNo(o.getDropDoorNo())
                .dropLandmark(o.getDropLandmark())
                .imageUrl(o.getImageUrl())
                .packageContents(o.getPackageContents())
                .declaredValue(o.getDeclaredValue())
                .pieceCount(o.getPieceCount())
                .isFragile(o.getIsFragile())
                .containsLiquid(o.getContainsLiquid())
                .containsBattery(o.getContainsBattery())
                .prohibitedItemsAccepted(o.getProhibitedItemsAccepted())
                .parcelDeclarationAccepted(o.getParcelDeclarationAccepted())
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
                .legTypeForRider(resolveLegTypeForRider(o, riderOrderApi, viewerRiderId))
                .originHubId(o.getOriginHubId())
                .destinationHubId(o.getDestinationHubId())
                .originHubName(originHub != null ? originHub.getName() : null)
                .originHubCity(originHub != null ? originHub.getCity() : null)
                .originHubLat(originHub != null ? originHub.getLat() : null)
                .originHubLng(originHub != null ? originHub.getLng() : null)
                .destinationHubName(destinationHub != null ? destinationHub.getName() : null)
                .destinationHubCity(destinationHub != null ? destinationHub.getCity() : null)
                .destinationHubLat(destinationHub != null ? destinationHub.getLat() : null)
                .destinationHubLng(destinationHub != null ? destinationHub.getLng() : null)
                .destinationHubCollectedByRider(o.getDestinationHubCollectedAt() != null)
                .weight(o.getWeight())
                .distanceKm(o.getDistanceKm())
                .paymentType(o.getPaymentType())
                .status(o.getStatus())
                .allowedNextStatuses(orderStatusTransitionGuard.allowedNextStatuses(
                        o.getServiceMode(), o.getDeliveryType(), o.getStatus()))
                .adminSelectableNextStatuses(orderStatusTransitionGuard.adminSelectableNextStatuses(
                        o.getServiceMode(), o.getDeliveryType(), o.getStatus()))
                .riderId(o.getRiderId())
                .pickupRiderId(o.getPickupRiderId())
                .deliveryRiderId(o.getDeliveryRiderId())
                .riderName(riderName)
                .riderPhone(riderPhone)
                .deliveryOtp(deliveryOtp)
                .pickupOtp(pickupOtp)
                .hubCollectionOtp(hubCollectionOtp)
                .isOtpVerified(o.getIsOtpVerified())
                .deliveryOtpGeneratedAt(
                        o.getDeliveryOtpGeneratedAt() != null ? o.getDeliveryOtpGeneratedAt().toString() : null)
                .deliveryOtpAttempts(o.getDeliveryOtpAttempts())
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
                .codAlreadyCollected(nz(o.getCodCollectedAmount()) > 0.0)
                .showCollectCodAction(resolveShowCollectCodAction(o, riderOrderApi, viewerRiderId))
                .canRateRider(Boolean.FALSE)
                .riderRatingSubmitted(Boolean.FALSE)
                .riderRating(null)
                .estimatedDeliveryTime(
                        o.getEstimatedDeliveryTime() != null ? o.getEstimatedDeliveryTime().toString() : null)
                .cutoffApplied(o.getCutoffApplied())
                .timelineEvents(orderTimelineService.timelineForOrder(o.getId()))
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .build();
        applyOutstationLegAmounts(dto, o, riderOrderApi, viewerRiderId);
        applyOutstationRiderFacingAddresses(dto, o, riderOrderApi, viewerRiderId, originHub, destinationHub);
        applyOutstationRiderContactVisibility(dto, o, riderOrderApi, viewerRiderId);
        // Pickup rider's job ends at AT_ORIGIN_HUB — show as DELIVERED to them.
        if (riderOrderApi && OutstationRiderLegPolicy.isSplitPickupRiderLegComplete(o, viewerRiderId)) {
            dto.setStatus(OrderStatus.DELIVERED);
            dto.setAllowedNextStatuses(java.util.Set.of());
            dto.setAdminSelectableNextStatuses(java.util.Set.of());
        }
        return dto;
    }

    private static String resolveLegTypeForRider(OrderEntity order, boolean riderOrderApi, Long viewerRiderId) {
        if (!riderOrderApi || order == null) {
            return "INCITY";
        }
        if (order.getServiceMode() != ServiceMode.OUTSTATION) {
            return "INCITY";
        }
        Long riderId = viewerRiderId != null ? viewerRiderId : order.getRiderId();
        if (riderId != null && order.getDeliveryRiderId() != null
                && Objects.equals(riderId, order.getDeliveryRiderId())) {
            Long pickupId = order.getPickupRiderId() != null ? order.getPickupRiderId() : order.getRiderId();
            if (!Objects.equals(pickupId, order.getDeliveryRiderId())) {
                return "DROP";
            }
            if (order.getStatus() == OrderStatus.OUT_FOR_DELIVERY) {
                return "DROP";
            }
        }
        return "PICKUP";
    }

    private static boolean resolveShowCollectCodAction(OrderEntity order, boolean riderOrderApi, Long viewerRiderId) {
        if (!riderOrderApi || order == null || order.getPaymentType() != PaymentType.COD) {
            return false;
        }
        if (nz(order.getCodCollectedAmount()) > 0.0) {
            return false;
        }
        if (!OutstationCodPolicy.pickupRiderCollectsCod(order)) {
            return false;
        }
        Long riderId = viewerRiderId != null ? viewerRiderId : order.getRiderId();
        if (riderId == null) {
            return false;
        }
        Long collector = OutstationCodPolicy.resolveCodCollectorRiderId(order);
        return collector != null && Objects.equals(riderId, collector);
    }

    private void applyOutstationLegAmounts(OrderResponseDTO dto, OrderEntity order, boolean riderOrderApi,
            Long viewerRiderId) {
        if (dto == null || order == null || order.getServiceMode() != ServiceMode.OUTSTATION) {
            return;
        }
        OutstationPayableLegSplit leg = OutstationPayableLegSplit.fromOrder(order);
        dto.setPickupAmount(leg.pickupAmount());
        dto.setHubToHubAmount(leg.hubToHubAmount());
        dto.setLastMileAmount(leg.lastMileAmount());
        if (riderOrderApi) {
            String legType = resolveLegTypeForRider(order, true, viewerRiderId);
            dto.setLegTypeForRider(legType);
            dto.setLegAmountForRider("DROP".equals(legType) ? leg.lastMileAmount() : leg.pickupAmount());
        }
    }

    private static void applyOutstationQuoteLegCosts(OrderEntity order, PricingService.OutstationBreakdown b) {
        if (order == null || b == null) {
            return;
        }
        order.setOutstationPickupCost(b.getPickupCost());
        order.setOutstationHubCost(b.getHubCost());
        order.setOutstationDropCost(b.getDropCost());
        order.setOutstationWeightCost(b.getWeightCost());
    }

    private static void applyOutstationRiderContactVisibility(
            OrderResponseDTO dto,
            OrderEntity order,
            boolean riderOrderApi,
            Long viewerRiderId) {
        if (!riderOrderApi || dto == null || order == null || order.getServiceMode() != ServiceMode.OUTSTATION) {
            return;
        }
        String legType = resolveLegTypeForRider(order, true, viewerRiderId);
        if ("DROP".equals(legType)) {
            dto.setSenderName(null);
            dto.setSenderPhone(null);
        } else {
            dto.setReceiverName(null);
            dto.setReceiverPhone(null);
        }
    }

    private static void applyOutstationRiderFacingAddresses(
            OrderResponseDTO dto,
            OrderEntity order,
            boolean riderOrderApi,
            Long viewerRiderId,
            HubEntity originHub,
            HubEntity destinationHub) {
        if (!riderOrderApi || dto == null || order == null || order.getServiceMode() != ServiceMode.OUTSTATION) {
            return;
        }
        Long riderId = viewerRiderId != null ? viewerRiderId : order.getRiderId();
        String legType = resolveLegTypeForRider(order, true, riderId);

        if ("DROP".equals(legType) && destinationHub != null) {
            // Delivery rider: collect parcel at destination hub, deliver to customer drop.
            String hubLine = formatHubAddressLine(destinationHub);
            dto.setPickupAddress(hubLine);
            dto.setPickupTag("HUB");
            dto.setPickupDoorNo(null);
            dto.setPickupLandmark(null);
            dto.setPickupLat(destinationHub.getLat());
            dto.setPickupLng(destinationHub.getLng());
            dto.setDistanceKm(order.getDropDistanceKm());
        }

        boolean pickupLegRider = "PICKUP".equals(legType);
        if (pickupLegRider && originHub != null) {
            // Pickup rider: handover at origin hub (not customer destination city).
            String hubLine = formatHubAddressLine(originHub);
            dto.setDropAddress(hubLine);
            dto.setDropTag("HUB");
            dto.setDropDoorNo(null);
            dto.setDropLandmark(null);
            dto.setDropLat(originHub.getLat());
            dto.setDropLng(originHub.getLng());
            dto.setDistanceKm(order.getPickupDistanceKm());
        }
    }

    private static String formatHubAddressLine(HubEntity hub) {
        if (hub == null) {
            return null;
        }
        return hub.getCity() == null || hub.getCity().isBlank()
                ? hub.getName() + " Hub"
                : hub.getName() + " Hub, " + hub.getCity();
    }

    private Map<Long, Integer> buildRiderRatingByOrderId(List<OrderEntity> orders) {
        if (orders == null || orders.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = orders.stream().map(OrderEntity::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return riderRatingRepository.findByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(RiderRatingEntity::getOrderId, RiderRatingEntity::getStars, (a, b) -> a));
    }

    private static void applyRiderRatingFlags(OrderResponseDTO dto, Integer riderStars) {
        if (dto == null) {
            return;
        }
        boolean submitted = riderStars != null;
        dto.setRiderRatingSubmitted(submitted);
        dto.setRiderRating(riderStars);
        boolean canRate = !submitted
                && dto.getRiderId() != null
                && dto.getStatus() == OrderStatus.DELIVERED;
        dto.setCanRateRider(canRate);
    }

    private HubEntity resolveHubById(Long hubId) {
        if (hubId == null) {
            return null;
        }
        return hubRepository.findById(hubId).orElse(null);
    }

    /**
     * Customer/admin: OTP from post-accept job states until verified. Rider app:
     * only {@code IN_TRANSIT} (handover).
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
            return (s == OrderStatus.IN_TRANSIT || s == OrderStatus.OUT_FOR_DELIVERY) ? otp : null;
        }
        if (o.getServiceMode() == ServiceMode.OUTSTATION) {
            return resolveOutstationDeliveryOtpForCustomer(o, s, otp);
        }
        return switch (s) {
            case RIDER_ACCEPTED, PAYMENT_PENDING, RIDER_ASSIGNED, PICKED_UP, IN_TRANSIT, OUT_FOR_DELIVERY -> otp;
            default -> null;
        };
    }

    /**
     * Customer outstation: delivery OTP only during last-mile / handover, not at pickup.
     */
    private static String resolveOutstationDeliveryOtpForCustomer(
            OrderEntity o, OrderStatus s, String otp) {
        if (Boolean.TRUE.equals(o.getIsOtpVerified())) {
            return null;
        }
        if (s == OrderStatus.CANCELLED
                || s == OrderStatus.EXPIRED
                || s == OrderStatus.FAILED
                || s == OrderStatus.RETURNED
                || s == OrderStatus.RETURNED_TO_SENDER
                || s == OrderStatus.SEARCHING_RIDER
                || s == OrderStatus.BOOKED
                || s == OrderStatus.PICKUP_ASSIGNED
                || s == OrderStatus.RIDER_ASSIGNED
                || s == OrderStatus.PICKED_UP
                || s == OrderStatus.AT_ORIGIN_HUB
                || s == OrderStatus.IN_TRANSIT
                || s == OrderStatus.AT_DESTINATION_HUB) {
            return null;
        }
        if (OutstationCodPolicy.isDoorToHub(o) && s == OrderStatus.AWAITING_HUB_COLLECTION) {
            return null;
        }
        return s == OrderStatus.OUT_FOR_DELIVERY ? otp : null;
    }

    /** Normalizes admin/API status input (legacy strings still accepted). */
    private static OrderStatus normalizeAdminTargetStatus(OrderEntity o, OrderStatus requested) {
        if (requested == null) {
            return null;
        }
        return OrderStatus.fromLegacy(requested.name());
    }

    /**
     * Outstation admin status changes must verify pickup/delivery OTP unless {@code adminOverride}.
     */
    private static void applyAdminOutstationOtpGates(
            OrderEntity o, OrderStatus target, String otp, boolean adminOverride) {
        if (o.getServiceMode() != ServiceMode.OUTSTATION || adminOverride) {
            return;
        }
        String provided = otp == null ? "" : otp.trim();
        if (target == OrderStatus.AT_ORIGIN_HUB
                && OutstationCodPolicy.isHubToDoor(o)
                && o.getStatus() == OrderStatus.BOOKED) {
            throw new RuntimeException("Use verify-hub-handover DROP for hub drop-off");
        }
        if (target == OrderStatus.PICKED_UP) {
            String pickupCode = o.getPickupOtp();
            if (pickupCode != null && !pickupCode.isBlank()) {
                if (provided.isEmpty()) {
                    throw new RuntimeException(
                            "Pickup OTP required. Enter the customer's pickup OTP or set adminOverride.");
                }
                if (!pickupCode.equals(provided)) {
                    throw new RuntimeException("Invalid pickup OTP");
                }
            }
            return;
        }
        if (target == OrderStatus.DELIVERED || target == OrderStatus.COLLECTED) {
            if (Boolean.TRUE.equals(o.getIsOtpVerified())) {
                return;
            }
            if (target == OrderStatus.COLLECTED) {
                throw new RuntimeException("Use verify-hub-handover COLLECT for hub collection");
            }
            String deliveryCode = o.getDeliveryOtp();
            if (deliveryCode != null && !deliveryCode.isBlank()) {
                if (provided.isEmpty()) {
                    throw new RuntimeException(
                            "Delivery OTP required. Enter the customer's delivery OTP or set adminOverride.");
                }
                if (!deliveryCode.equals(provided)) {
                    throw new RuntimeException("Invalid delivery OTP");
                }
            }
        }
    }

    /**
     * Customer outstation: pickup OTP after rider assigned, until parcel is picked up.
     */
    private static String resolvePickupOtpForResponse(OrderEntity o, boolean riderOrderApi) {
        if (riderOrderApi || o.getServiceMode() != ServiceMode.OUTSTATION) {
            return null;
        }
        String otp = o.getPickupOtp();
        if (otp == null || otp.isBlank()) {
            return null;
        }
        OrderStatus s = o.getStatus();
        if (OutstationCodPolicy.isHubToDoor(o)) {
            return null;
        }
        if (!OrderStatus.isOutstationPickupAssigned(s)) {
            return null;
        }
        return o.getPickupRiderId() != null ? otp : null;
    }

    /** Customer: collection OTP at destination hub (DOOR_TO_HUB). */
    private static String resolveHubCollectionOtpForResponse(OrderEntity o, boolean riderOrderApi) {
        if (riderOrderApi || o.getServiceMode() != ServiceMode.OUTSTATION) {
            return null;
        }
        if (!OutstationCodPolicy.isDoorToHub(o) || o.getStatus() != OrderStatus.AWAITING_HUB_COLLECTION) {
            return null;
        }
        if (Boolean.TRUE.equals(o.getIsOtpVerified())) {
            return null;
        }
        String otp = o.getDeliveryOtp();
        return otp == null || otp.isBlank() ? null : otp;
    }

    private void applyOutstationOtpsOnCreate(OrderEntity order) {
        // OTPs are generated on admin rider assignment / hub-ready transitions per spec.
    }

    private void recordSenderCodAtHubIfNeeded(
            OrderEntity order, VerifyHubHandoverRequestDTO dto, boolean adminOverride) {
        if (order.getPaymentType() != PaymentType.COD) {
            return;
        }
        if (nz(order.getCodCollectedAmount()) > 0.0) {
            return;
        }
        if (adminOverride) {
            return;
        }
        if (dto.getCodCollectionMode() == null || dto.getCodCollectionMode().isBlank()) {
            throw new RuntimeException("COD: codCollectionMode (CASH or QR) is required when collecting from sender at hub");
        }
        recordSenderCodCollection(order, dto.getCodCollectionMode());
    }

    private void validateAdminOutstationStatusUpdate(
            OrderEntity order, OrderStatus target, String codCollectionMode, boolean adminOverride) {
        if (order == null || target == null || order.getServiceMode() != ServiceMode.OUTSTATION) {
            return;
        }
        if (target == OrderStatus.OUT_FOR_DELIVERY
                && isOutstationDoorDeliveryType(order.getDeliveryType())
                && order.getDeliveryRiderId() == null) {
            throw new RuntimeException(
                    "Assign a delivery rider first — Out for Delivery is set automatically when a delivery rider is assigned");
        }
        if (target == OrderStatus.PICKED_UP
                && OutstationCodPolicy.pickupRiderCollectsCod(order)
                && order.getPaymentType() == PaymentType.COD
                && nz(order.getCodCollectedAmount()) <= 0.0
                && !adminOverride) {
            if (codCollectionMode == null || codCollectionMode.isBlank()) {
                throw new RuntimeException(
                        "COD: select collection mode (CASH or QR) when confirming pickup from sender");
            }
            recordSenderCodCollection(order, codCollectionMode);
        }
        if (target == OrderStatus.AT_ORIGIN_HUB
                && OutstationCodPolicy.isHubToDoor(order)
                && order.getPaymentType() == PaymentType.COD
                && nz(order.getCodCollectedAmount()) <= 0.0
                && !adminOverride) {
            if (codCollectionMode == null || codCollectionMode.isBlank()) {
                throw new RuntimeException(
                        "COD: select collection mode (CASH or QR) when recording hub receipt");
            }
            recordSenderCodCollection(order, codCollectionMode);
        }
    }

    private void recordSenderCodCollection(OrderEntity order, String codCollectionMode) {
        CodCollectionMode mode = CodCollectionMode.parseClientValue(codCollectionMode);
        double collected = round2(nz(order.getTotalAmount()));
        if (collected <= 0) {
            throw new RuntimeException("Invalid order total for COD collection");
        }
        order.setCodCollectionMode(mode);
        order.setCodCollectedAmount(collected);
        order.setCodSettlementStatus(CodSettlementStatus.PENDING);
    }

    private void publishAdminStatusEvent(OrderEntity saved, OrderStatus target) {
        UserOrderEventDTO evt = new UserOrderEventDTO();
        evt.setOrderId(saved.getId());
        evt.setEvent("status_updated");
        evt.setEventType("status_updated");
        evt.setEventVersion(1);
        evt.setTsEpochMs(Instant.now().toEpochMilli());
        evt.setSource("backend");
        evt.setStatus(saved.getStatus().name());
        evt.setServiceMode(saved.getServiceMode() == null ? null : saved.getServiceMode().name());
        evt.setStage(saved.getStatus().name());
        evt.setRiderId(saved.getRiderId());
        messagingTemplate.convertAndSend("/topic/users/" + saved.getUserId() + "/order-events", evt);
        adminOrderTopicPublisher.publishStatusUpdated(saved);

        if (saved.getStatus() == OrderStatus.DELIVERED
                || saved.getStatus() == OrderStatus.COLLECTED
                || saved.getStatus() == OrderStatus.CANCELLED
                || saved.getStatus() == OrderStatus.RETURNED
                || saved.getStatus() == OrderStatus.EXPIRED
                || saved.getStatus() == OrderStatus.FAILED) {
            userActiveOrderTopicPublisher.publishReleased(saved.getUserId(), saved.getId());
        } else {
            userActiveOrderTopicPublisher.publishStatusUpdated(
                    saved.getUserId(),
                    saved.getId(),
                    saved.getStatus().name(),
                    saved.getServiceMode() == null ? null : saved.getServiceMode().name(),
                    saved.getRiderId());
        }
        if (target == OrderStatus.DELIVERED && saved.getRiderId() != null) {
            riderWalletService.settleOrderDelivered(
                    saved,
                    saved.getPaymentType() == PaymentType.COD ? saved.getCodCollectionMode() : null,
                    saved.getCodCollectedAmount(),
                    null,
                    "ADMIN");
        }
        sendMilestoneUserStatusPush(saved, target);
        publishAdminStatusToAssignedRiders(saved, target);
    }

    private void publishAdminStatusToAssignedRiders(OrderEntity saved, OrderStatus target) {
        if (saved == null) {
            return;
        }
        Set<Long> riderIds = new HashSet<>();
        if (saved.getPickupRiderId() != null) {
            riderIds.add(saved.getPickupRiderId());
        }
        if (saved.getDeliveryRiderId() != null) {
            riderIds.add(saved.getDeliveryRiderId());
        }
        if (saved.getRiderId() != null) {
            riderIds.add(saved.getRiderId());
        }
        if (riderIds.isEmpty()) {
            return;
        }

        ServiceMode mode = saved.getServiceMode();
        String event = resolveRiderSocketEventForAdminStatus(target);
        for (Long riderId : riderIds) {
            try {
                if (OutstationRiderLegPolicy.isSplitPickupRiderLegComplete(saved, riderId)) {
                    // Pickup rider was released at origin hub — no further socket updates.
                    continue;
                }
                if (!OutstationRiderLegPolicy.shouldRiderHaveActiveOrder(saved, riderId)) {
                    continue;
                }
                riderActiveOrderTopicPublisher.publish(
                        riderId,
                        saved.getId(),
                        saved.getStatus(),
                        event,
                        "admin_status_update",
                        null,
                        mode);
                if ("status_updated".equals(event)) {
                    riderActiveOrderTopicPublisher.publishSnapshot(
                            riderId,
                            saved.getId(),
                            saved.getStatus(),
                            mode);
                }
            } catch (Exception ignored) {
                // Keep admin update successful even if rider socket publish fails.
            }
        }

        if (target == OrderStatus.DELIVERED || target == OrderStatus.COLLECTED) {
            for (Long riderId : riderIds) {
                markRiderAvailableAfterDelivery(riderId);
            }
        }
    }

    private static String resolveRiderSocketEventForAdminStatus(OrderStatus target) {
        if (target == null) {
            return "status_updated";
        }
        return switch (target) {
            case DELIVERED, COLLECTED -> "delivered";
            case CANCELLED, EXPIRED, FAILED, RETURNED, RETURNED_TO_SENDER -> "released";
            default -> "status_updated";
        };
    }

    private void transitionStatus(OrderEntity order, OrderStatus toStatus) {
        if (order == null || toStatus == null) {
            return;
        }
        OrderStatus from = order.getStatus();
        if (from == toStatus) {
            return;
        }
        if (order.getServiceMode() == ServiceMode.OUTSTATION) {
            orderStatusTransitionGuard.ensureAllowed(
                    order.getServiceMode(), order.getDeliveryType(), from, toStatus);
        } else {
            orderStatusTransitionGuard.ensureAllowed(order.getServiceMode(), from, toStatus);
        }
        order.setStatus(toStatus);
    }

    private static OrderStatus resolveStatusAfterAdminRiderAssign(
            OrderEntity order, Long pickupRiderId, Long deliveryRiderId) {
        OrderStatus current = order.getStatus() != null ? order.getStatus() : OrderStatus.BOOKED;
        boolean assigningDelivery = deliveryRiderId != null;
        boolean assigningPickupOnly = pickupRiderId != null && deliveryRiderId == null;

        if (order.getServiceMode() == ServiceMode.OUTSTATION
                && assigningDelivery
                && isOutstationDoorDeliveryType(order.getDeliveryType())
                && isOutstationDestinationDeliveryPhase(current)) {
            if (current == OrderStatus.OUT_FOR_DELIVERY) {
                return current;
            }
            return OrderStatus.OUT_FOR_DELIVERY;
        }
        if (assigningPickupOnly) {
            if (current == OrderStatus.BOOKED) {
                return order.getServiceMode() == ServiceMode.OUTSTATION
                        ? OrderStatus.PICKUP_ASSIGNED
                        : OrderStatus.RIDER_ASSIGNED;
            }
            return current;
        }
        if (current == OrderStatus.BOOKED || current == OrderStatus.SEARCHING_RIDER) {
            return order.getServiceMode() == ServiceMode.OUTSTATION
                    ? OrderStatus.PICKUP_ASSIGNED
                    : OrderStatus.RIDER_ASSIGNED;
        }
        if (assigningDelivery && current == OrderStatus.OUT_FOR_DELIVERY) {
            return current;
        }
        if (assigningDelivery && isOutstationDestinationDeliveryPhase(current)) {
            return OrderStatus.OUT_FOR_DELIVERY;
        }
        return current != OrderStatus.PICKUP_ASSIGNED && current != OrderStatus.RIDER_ASSIGNED
                ? current
                : (order.getServiceMode() == ServiceMode.OUTSTATION
                        ? OrderStatus.PICKUP_ASSIGNED
                        : OrderStatus.RIDER_ASSIGNED);
    }

    private void assertOnlinePaidBeforeRiderAssignment(OrderEntity o) {
        if (o == null || o.getPaymentType() != PaymentType.ONLINE) {
            return;
        }
        if (!isOrderPaidOnline(o)) {
            throw new RuntimeException("Online order must be paid before assigning a rider");
        }
    }

    private static boolean isOutstationDoorDeliveryType(String deliveryType) {
        if (deliveryType == null || deliveryType.isBlank()) {
            return false;
        }
        String t = deliveryType.trim().toUpperCase(Locale.ROOT);
        return "HUB_TO_DOOR".equals(t) || "DOOR_TO_DOOR".equals(t);
    }

    private static boolean isOutstationDestinationDeliveryPhase(OrderStatus status) {
        return status == OrderStatus.AT_DESTINATION_HUB || status == OrderStatus.OUT_FOR_DELIVERY;
    }

    private void appendTimeline(
            OrderEntity order,
            OrderStatus status,
            String eventType,
            Long hubId,
            Long riderId,
            String notes) {
        orderTimelineService.appendEvent(order, status, eventType, hubId, riderId, null, notes);
    }

    /**
     * Riders do not need GST, platform fee, coupon, or gateway pricing components —
     * only operational fields
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
        // Rider should primarily see earnings; keep totalAmount only for COD collection
        // context.
        if (dto.getPaymentType() != PaymentType.COD) {
            dto.setTotalAmount(null);
            dto.setCodCollectedAmount(null);
            dto.setCodCollectionMode(null);
            dto.setCodSettlementStatus(null);
            // OUTSTATION ONLINE: show this rider's leg fare (pickup-to-hub or hub-to-door),
            // not full trip total.
            if (dto.getServiceMode() == ServiceMode.OUTSTATION && dto.getLegAmountForRider() != null) {
                dto.setTotalAmount(dto.getLegAmountForRider());
            }
        }
        return dto;
    }

    private static Long resolveDisplayRiderId(OrderEntity o, boolean riderOrderApi) {
        if (o == null) {
            return null;
        }
        if (riderOrderApi) {
            return o.getRiderId();
        }
        if (o.getServiceMode() != ServiceMode.OUTSTATION) {
            return o.getRiderId();
        }
        OrderStatus s = o.getStatus();
        if (s == OrderStatus.OUT_FOR_DELIVERY && o.getDeliveryRiderId() != null) {
            return o.getDeliveryRiderId();
        }
        if (OrderStatus.isOutstationPickupAssigned(s) || s == OrderStatus.PICKED_UP) {
            if (o.getPickupRiderId() != null) {
                return o.getPickupRiderId();
            }
        }
        return null;
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

    /**
     * ONLINE pre-paid orders: wallet settlement uses {@code order.totalAmount};
     * gateway payment is reflected as PAID.
     */
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
            throw new RuntimeException(
                    "Send all of subtotal, gstAmount, platformFee, totalAmount, or omit all for server pricing");
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

    /**
     * Ensures outstation hub IDs belong to the zones that contain pickup / drop coordinates.
     * Any active hub in the pickup zone is allowed for origin; any in the drop zone for destination.
     */
    private void requireOutstationHubsMatchZones(
            Double pickupLat, Double pickupLng,
            Double dropLat, Double dropLng,
            HubEntity origin, HubEntity dest) {
        Long pickupZoneId = zoneService.resolveServingZoneIdAt(pickupLat, pickupLng)
                .orElseThrow(() -> new RuntimeException("Pickup location is not inside a service zone"));
        Long dropZoneId = zoneService.resolveServingZoneIdAt(dropLat, dropLng)
                .orElseThrow(() -> new RuntimeException("Drop location is not inside a service zone"));

        if (origin.getZoneId() == null) {
            throw new RuntimeException("Origin hub is not linked to a zone");
        }
        if (!Objects.equals(origin.getZoneId(), pickupZoneId)) {
            throw new RuntimeException("Origin hub does not belong to the pickup zone");
        }
        if (dest.getZoneId() == null) {
            throw new RuntimeException("Destination hub is not linked to a zone");
        }
        if (!Objects.equals(dest.getZoneId(), dropZoneId)) {
            throw new RuntimeException("Destination hub does not belong to the drop zone");
        }
    }

    private record LegKm(double pickupKm, double hubKm, double dropKm) {
    }

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

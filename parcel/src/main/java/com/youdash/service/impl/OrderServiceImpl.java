package com.youdash.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.OrderTrackingDTO;
import com.youdash.dto.PricingCalculateRequestDTO;
import com.youdash.dto.PricingCalculateResponseDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.PackageCategoryEntity;
import com.youdash.entity.PackageItemEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.UserEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.model.FulfillmentType;
import com.youdash.model.OrderStatus;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.PackageCategoryRepository;
import com.youdash.repository.PackageItemRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.OrderNotificationService;
import com.youdash.service.OrderService;
import com.youdash.service.DistanceService;
import com.youdash.service.PricingCalculateService;
import com.youdash.service.ZoneGeoService;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private PackageCategoryRepository packageCategoryRepository;

    @Autowired
    private PackageItemRepository packageItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private OrderNotificationService orderNotificationService;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private PricingCalculateService pricingCalculateService;

    @Autowired
    private ZoneGeoService zoneGeoService;

    @Override
    public ApiResponse<OrderResponseDTO> createOrder(OrderRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (dto.getUserId() == null) {
                throw new RuntimeException("UserId is required");
            }

            if (dto.getPackageCategoryId() == null) {
                throw new RuntimeException("packageCategoryId is required");
            }

            if (dto.getPickupLat() == null || dto.getPickupLng() == null
                    || dto.getDeliveryLat() == null || dto.getDeliveryLng() == null) {
                throw new RuntimeException("pickupLat, pickupLng, deliveryLat, deliveryLng are required");
            }
            validateLatLng(dto.getPickupLat(), dto.getPickupLng(), "pickup");
            validateLatLng(dto.getDeliveryLat(), dto.getDeliveryLng(), "delivery");

            boolean incity = zoneGeoService.isSameIncityZone(
                    dto.getPickupLat(), dto.getPickupLng(), dto.getDeliveryLat(), dto.getDeliveryLng());
            if (incity && dto.getVehicleTypeId() == null) {
                throw new RuntimeException("vehicleTypeId is required for INCITY orders");
            }
            if (!incity && dto.getVehicleTypeId() != null) {
                // allow but pricing ignores vehicle for outstation
            }

            VehicleEntity vehicle = null;
            if (dto.getVehicleTypeId() != null) {
                vehicle = vehicleRepository.findById(Objects.requireNonNull(dto.getVehicleTypeId()))
                        .orElseThrow(() -> new RuntimeException("Invalid vehicleTypeId: " + dto.getVehicleTypeId()));
                if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
                    throw new RuntimeException("Selected vehicle is not active");
                }
                if (vehicle.getPricePerKm() == null) {
                    throw new RuntimeException("Vehicle price not configured");
                }
            }

            UserEntity user = userRepository.findById(dto.getUserId())
                    .filter(u -> Boolean.TRUE.equals(u.getActive()))
                    .orElseThrow(() -> new RuntimeException("Invalid userId: " + dto.getUserId()));

            PackageCategoryEntity category = packageCategoryRepository.findById(Objects.requireNonNull(dto.getPackageCategoryId()))
                    .orElseThrow(() -> new RuntimeException("Invalid packageCategoryId: " + dto.getPackageCategoryId()));

            if (!Boolean.TRUE.equals(category.getIsActive())) {
                throw new RuntimeException("Selected package category is not active");
            }

            Double resolvedDistanceKm = distanceService.calculateDistanceKm(
                    dto.getPickupLat(), dto.getPickupLng(), dto.getDeliveryLat(), dto.getDeliveryLng()
            );
            if (resolvedDistanceKm == null || resolvedDistanceKm <= 0) {
                throw new RuntimeException("Resolved distance must be greater than 0");
            }

            PricingCalculateRequestDTO priceReq = new PricingCalculateRequestDTO();
            priceReq.setPickupLat(dto.getPickupLat());
            priceReq.setPickupLng(dto.getPickupLng());
            priceReq.setDropLat(dto.getDeliveryLat());
            priceReq.setDropLng(dto.getDeliveryLng());
            priceReq.setVehicleId(incity ? dto.getVehicleTypeId() : null);
            priceReq.setWeightKg(dto.getWeight() == null ? 0.0 : dto.getWeight());
            if (!incity) {
                String dopt = dto.getDeliveryOption();
                if (dopt == null || dopt.isBlank()) {
                    dopt = FulfillmentType.HUB_TO_DOOR;
                } else {
                    dopt = dopt.trim().toUpperCase();
                }
                priceReq.setDeliveryOption(dopt);
            }

            PricingCalculateResponseDTO pricing = pricingCalculateService.computePricing(priceReq);

            String senderPhone = dto.getSenderPhone();
            if (senderPhone == null || senderPhone.trim().isEmpty()) {
                senderPhone = user.getPhoneNumber();
            } else {
                senderPhone = senderPhone.trim();
            }
            String senderName = dto.getSenderName();
            if (senderName != null) {
                senderName = senderName.trim();
            }

            OrderEntity order = new OrderEntity();

            order.setUserId(dto.getUserId());
            order.setPickupAddress(dto.getPickupAddress());
            order.setDeliveryAddress(dto.getDeliveryAddress());
            order.setPickupLat(dto.getPickupLat());
            order.setPickupLng(dto.getPickupLng());
            order.setDeliveryLat(dto.getDeliveryLat());
            order.setDeliveryLng(dto.getDeliveryLng());

            order.setSenderName(senderName);
            order.setSenderPhone(senderPhone);

            order.setReceiverName(dto.getReceiverName());
            order.setReceiverPhone(dto.getReceiverPhone());
            order.setPackageCategoryId(dto.getPackageCategoryId());

            if (dto.getDescription() == null || dto.getDescription().trim().isEmpty()) {
                order.setDescription(category.getDefaultDescription());
            } else {
                order.setDescription(dto.getDescription());
            }

            order.setWeight(dto.getWeight());
            order.setImageUrl(dto.getImageUrl());
            order.setVehicleTypeId(incity ? dto.getVehicleTypeId() : null);
            order.setDistanceKm(resolvedDistanceKm);

            order.setTotalAmount(pricing.getTotalAmount());
            order.setBaseAmount(java.math.BigDecimal.valueOf(pricing.getSubTotalAfterMin()).setScale(2, java.math.RoundingMode.HALF_UP));
            order.setDeliveryFee(java.math.BigDecimal.valueOf(pricing.getSubTotalAfterMin()).setScale(2, java.math.RoundingMode.HALF_UP));
            order.setPlatformFee(java.math.BigDecimal.valueOf(pricing.getPlatformFee()).setScale(2, java.math.RoundingMode.HALF_UP));
            order.setDiscountAmount(java.math.BigDecimal.ZERO.setScale(2, java.math.RoundingMode.HALF_UP));
            order.setGstAmount(java.math.BigDecimal.valueOf(pricing.getGstAmount()).setScale(2, java.math.RoundingMode.HALF_UP));
            if (vehicle != null && vehicle.getPricePerKm() != null) {
                order.setPricePerKmUsed(java.math.BigDecimal.valueOf(vehicle.getPricePerKm()).setScale(2, java.math.RoundingMode.HALF_UP));
            }

            order.setPaymentType(dto.getPaymentType());
            order.setScheduledDate(dto.getScheduledDate());
            order.setTimeSlot(dto.getTimeSlot());

            String fulfillmentType;
            if (incity) {
                fulfillmentType = FulfillmentType.INCITY;
            } else {
                String opt = dto.getDeliveryOption();
                if (opt == null || opt.isBlank()) {
                    fulfillmentType = FulfillmentType.HUB_TO_DOOR;
                } else {
                    String u = opt.trim().toUpperCase();
                    fulfillmentType = switch (u) {
                        case "DOOR_TO_DOOR" -> FulfillmentType.DOOR_TO_DOOR;
                        case "DOOR_TO_HUB" -> FulfillmentType.DOOR_TO_HUB;
                        case "HUB_TO_DOOR" -> FulfillmentType.HUB_TO_DOOR;
                        default -> throw new RuntimeException(
                                "deliveryOption must be DOOR_TO_DOOR, DOOR_TO_HUB, or HUB_TO_DOOR");
                    };
                }
            }
            order.setFulfillmentType(fulfillmentType);

            // 6. Handle Package Items
            if (dto.getPackageItemIds() != null && !dto.getPackageItemIds().isEmpty()) {
                List<PackageItemEntity> selectedItems = packageItemRepository.findAllById(Objects.requireNonNull(dto.getPackageItemIds()));
                
                // Validate all items exist
                if (selectedItems.size() != dto.getPackageItemIds().size()) {
                    throw new RuntimeException("One or more selected package items are invalid");
                }
                
                // Validate items belong to category and are active
                for (PackageItemEntity item : selectedItems) {
                    if (!item.getPackageCategoryId().equals(dto.getPackageCategoryId())) {
                        throw new RuntimeException("Item '" + item.getName() + "' does not belong to the selected category");
                    }
                    if (!Boolean.TRUE.equals(item.getIsActive())) {
                        throw new RuntimeException("Item '" + item.getName() + "' is not active");
                    }
                }
                
                String itemsCsv = dto.getPackageItemIds().stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
                order.setPackageItems(itemsCsv);
            }

            // Default values
            order.setOrderId("YP-" + System.currentTimeMillis());
            boolean cod = dto.getPaymentType() != null && dto.getPaymentType().trim().equalsIgnoreCase("COD");
            order.setStatus(cod ? OrderStatus.READY_FOR_ASSIGNMENT : OrderStatus.CREATED);
            order.setPaymentStatus("PENDING");

            OrderEntity savedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(savedOrder));
            response.setMessage("Order created successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            try {
                orderNotificationService.onOrderCreated(savedOrder, cod);
            } catch (Exception ignored) {
                // Never fail API for notification issues
            }

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<OrderResponseDTO>> getOrdersByUserId(Long userId) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            if (userId == null) {
                throw new RuntimeException("UserId is required");
            }
            List<OrderEntity> orders = orderRepository.findByUserId(userId);
            List<OrderResponseDTO> dtos = orders.stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());

            response.setData(dtos);
            response.setMessage("Orders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage("Failed to fetch orders: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> getOrderById(Long id) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            response.setData(mapToResponseDTO(order));
            response.setMessage("Order fetched successfully");
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
    public ApiResponse<OrderTrackingDTO> getOrderTracking(Long id) {
        ApiResponse<OrderTrackingDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));
            OrderTrackingDTO t = new OrderTrackingDTO();
            t.setOrderId(order.getId());
            t.setOrderPublicId(order.getOrderId());
            t.setStatus(order.getStatus());
            t.setRiderId(order.getRiderId());
            t.setUpdatedAt(order.getUpdatedAt());
            if (order.getRiderId() != null) {
                riderRepository.findById(order.getRiderId()).ifPresent(r -> {
                    t.setRiderName(r.getName());
                    t.setRiderPhone(r.getPhone());
                    t.setRiderLat(r.getCurrentLat());
                    t.setRiderLng(r.getCurrentLng());
                });
            }
            response.setData(t);
            response.setMessage("Order tracking fetched successfully");
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
    public ApiResponse<OrderResponseDTO> updateOrderStatus(Long id, String status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            List<String> allowedStatuses = List.of(
                    OrderStatus.CREATED,
                    OrderStatus.READY_FOR_ASSIGNMENT,
                    OrderStatus.ASSIGNED,
                    OrderStatus.ACCEPTED,
                    OrderStatus.PICKED_UP,
                    "PICKED",
                    OrderStatus.IN_TRANSIT,
                    OrderStatus.DELIVERED,
                    OrderStatus.CANCELLED,
                    OrderStatus.AT_SOURCE_HUB,
                    OrderStatus.IN_TRANSIT_TO_DEST_HUB,
                    OrderStatus.AT_DESTINATION_HUB,
                    OrderStatus.READY_FOR_DELIVERY,
                    OrderStatus.ASSIGNED_TO_DELIVERY_RIDER,
                    OrderStatus.OUT_FOR_DELIVERY,
                    OrderStatus.DELIVERED_AT_HUB,
                    OrderStatus.FAILED_AT_HUB,
                    OrderStatus.RETURN_INITIATED);
            if (!allowedStatuses.contains(status)) {
                throw new RuntimeException("Invalid status");
            }
            if ("PICKED".equals(status)) {
                status = OrderStatus.PICKED_UP;
            }

            String previousStatus = order.getStatus();
            order.setStatus(status);
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Order status updated successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            if (OrderStatus.DELIVERED.equals(status) && (previousStatus == null || !OrderStatus.DELIVERED.equals(previousStatus))) {
                try {
                    orderNotificationService.onDelivered(updatedOrder);
                } catch (Exception ignored) {
                    // Never fail API for notification issues
                }
            }

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> assignRider(Long id, Long riderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            if (!Set.of(OrderStatus.CREATED, OrderStatus.READY_FOR_ASSIGNMENT).contains(order.getStatus())) {
                throw new RuntimeException("Rider can only be assigned when order is CREATED or READY_FOR_ASSIGNMENT");
            }
            if (!isAssignmentPaymentOk(order)) {
                throw new RuntimeException("Order payment must be PAID (or COD) before assigning a rider");
            }
            RiderEntity rider = riderRepository.findById(Objects.requireNonNull(riderId))
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));
            assertRiderAssignable(rider);

            order.setRiderId(riderId);
            order.setStatus(OrderStatus.ASSIGNED);
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Rider assigned successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            try {
                orderNotificationService.onPickupRiderAssigned(updatedOrder);
            } catch (Exception ignored) {
                // Never fail API for notification issues
            }

        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> cancelOrder(Long id) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            if (!Set.of(OrderStatus.CREATED, OrderStatus.READY_FOR_ASSIGNMENT, OrderStatus.ASSIGNED).contains(order.getStatus())) {
                throw new RuntimeException("Cannot cancel order in " + order.getStatus() + " status");
            }

            if (OrderStatus.ASSIGNED.equals(order.getStatus())) {
                order.setRiderId(null);
            }

            order.setStatus(OrderStatus.CANCELLED);
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Order cancelled successfully");
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
    public ApiResponse<OrderResponseDTO> updateOrder(Long id, OrderRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            if (!Set.of(OrderStatus.CREATED, OrderStatus.READY_FOR_ASSIGNMENT).contains(order.getStatus())) {
                throw new RuntimeException("Order can only be updated in CREATED or READY_FOR_ASSIGNMENT status");
            }

            // Update allowed fields
            if (dto.getPickupAddress() != null) order.setPickupAddress(dto.getPickupAddress());
            if (dto.getDeliveryAddress() != null) order.setDeliveryAddress(dto.getDeliveryAddress());
            if (dto.getDescription() != null) order.setDescription(dto.getDescription());
            
            if (dto.getWeight() != null) {
                if (order.getVehicleTypeId() != null) {
                    VehicleEntity vehicle = vehicleRepository.findById(Objects.requireNonNull(order.getVehicleTypeId()))
                            .orElseThrow(() -> new RuntimeException("Vehicle not found"));
                    if (vehicle.getMaxWeight() != null && dto.getWeight() > vehicle.getMaxWeight()) {
                        throw new RuntimeException("Weight exceeds vehicle capacity");
                    }
                }
                order.setWeight(dto.getWeight());
            }

            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Order updated successfully");
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

    private OrderResponseDTO mapToResponseDTO(OrderEntity order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setOrderId(order.getOrderId());
        dto.setUserId(order.getUserId());
        dto.setPickupAddress(order.getPickupAddress());
        dto.setDeliveryAddress(order.getDeliveryAddress());
        dto.setPickupLat(order.getPickupLat());
        dto.setPickupLng(order.getPickupLng());
        dto.setDeliveryLat(order.getDeliveryLat());
        dto.setDeliveryLng(order.getDeliveryLng());
        dto.setSenderName(order.getSenderName());
        dto.setSenderPhone(order.getSenderPhone());
        dto.setReceiverName(order.getReceiverName());
        dto.setReceiverPhone(order.getReceiverPhone());
        
        dto.setPackageCategoryId(order.getPackageCategoryId());
        // Fetch Category Name
        if (order.getPackageCategoryId() != null) {
            packageCategoryRepository.findById(Objects.requireNonNull(order.getPackageCategoryId())).ifPresent(c -> {
                dto.setPackageCategoryName(c.getName());
            });
        }

        dto.setDescription(order.getDescription());
        dto.setWeight(order.getWeight());
        dto.setImageUrl(order.getImageUrl());
        
        dto.setVehicleTypeId(order.getVehicleTypeId());
        // Fetch Vehicle Name
        if (order.getVehicleTypeId() != null) {
            vehicleRepository.findById(Objects.requireNonNull(order.getVehicleTypeId())).ifPresent(v -> {
                dto.setVehicleName(v.getName());
            });
        }

        dto.setDistanceKm(order.getDistanceKm());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setBaseAmount(order.getBaseAmount() == null ? null : order.getBaseAmount().doubleValue());
        dto.setPlatformFee(order.getPlatformFee() == null ? null : order.getPlatformFee().doubleValue());
        dto.setDeliveryFee(order.getDeliveryFee() == null ? null : order.getDeliveryFee().doubleValue());
        dto.setDiscountAmount(order.getDiscountAmount() == null ? null : order.getDiscountAmount().doubleValue());
        dto.setGstAmount(order.getGstAmount() == null ? null : order.getGstAmount().doubleValue());
        dto.setPricePerKmUsed(order.getPricePerKmUsed() == null ? null : order.getPricePerKmUsed().doubleValue());
        dto.setPaymentType(order.getPaymentType());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setPaymentMethod(order.getPaymentMethod());
        dto.setPaymentCreatedAt(order.getPaymentCreatedAt());
        dto.setPaymentUpdatedAt(order.getPaymentUpdatedAt());
        dto.setRazorpayOrderId(order.getRazorpayOrderId());
        dto.setRazorpayPaymentId(order.getRazorpayPaymentId());
        dto.setStatus(order.getStatus());
        dto.setRiderId(order.getRiderId());
        dto.setDeliveryRiderId(order.getDeliveryRiderId());
        dto.setFulfillmentType(order.getFulfillmentType());
        dto.setScheduledDate(order.getScheduledDate());
        dto.setTimeSlot(order.getTimeSlot());
        dto.setCreatedAt(order.getCreatedAt());

        // Decode Package Items
        if (order.getPackageItems() != null && !order.getPackageItems().isEmpty()) {
            java.util.List<Long> itemIds = java.util.Arrays.stream(order.getPackageItems().split(","))
                    .map(Long::valueOf)
                    .collect(Collectors.toList());
            dto.setPackageItemIds(itemIds);
            
            List<PackageItemEntity> items = packageItemRepository.findAllById(Objects.requireNonNull(itemIds));
            List<String> itemNames = items.stream()
                    .map(PackageItemEntity::getName)
                    .collect(Collectors.toList());
            dto.setPackageItemNames(itemNames);
        }

        return dto;
    }

    @Override
    public ApiResponse<List<OrderResponseDTO>> listUnassignedOrders() {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            List<OrderEntity> list = orderRepository
                    .findByStatusInAndRiderIdIsNull(
                            List.of(OrderStatus.CREATED, OrderStatus.READY_FOR_ASSIGNMENT),
                            Sort.by(Sort.Direction.DESC, "createdAt"))
                    .stream()
                    .filter(this::isAssignmentPaymentOk)
                    .collect(Collectors.toList());
            response.setData(list.stream().map(this::mapToResponseDTO).collect(Collectors.toList()));
            response.setMessage("Unassigned orders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(list.size());
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
    public ApiResponse<OrderResponseDTO> updateHubStatus(Long orderId, String status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (orderId == null || status == null || status.isBlank()) {
                throw new RuntimeException("orderId and status are required");
            }
            String next = status.trim().toUpperCase();
            if (!Set.of(OrderStatus.AT_SOURCE_HUB, OrderStatus.IN_TRANSIT_TO_DEST_HUB, OrderStatus.AT_DESTINATION_HUB).contains(next)) {
                throw new RuntimeException("Invalid hub status");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            if (isIncityOrder(order)) {
                throw new RuntimeException("Hub status applies only to outstation orders");
            }
            String cur = order.getStatus();
            if (OrderStatus.PICKED_UP.equals(cur) && !OrderStatus.AT_SOURCE_HUB.equals(next)) {
                throw new RuntimeException("From PICKED_UP only AT_SOURCE_HUB is allowed");
            }
            if (OrderStatus.AT_SOURCE_HUB.equals(cur) && !OrderStatus.IN_TRANSIT_TO_DEST_HUB.equals(next)) {
                throw new RuntimeException("From AT_SOURCE_HUB only IN_TRANSIT_TO_DEST_HUB is allowed");
            }
            if (OrderStatus.IN_TRANSIT_TO_DEST_HUB.equals(cur) && !OrderStatus.AT_DESTINATION_HUB.equals(next)) {
                throw new RuntimeException("From IN_TRANSIT_TO_DEST_HUB only AT_DESTINATION_HUB is allowed");
            }
            if (!OrderStatus.PICKED_UP.equals(cur) && !OrderStatus.AT_SOURCE_HUB.equals(cur) && !OrderStatus.IN_TRANSIT_TO_DEST_HUB.equals(cur)) {
                throw new RuntimeException("Hub status cannot be updated from " + cur);
            }
            order.setStatus(next);
            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Hub status updated");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            try {
                orderNotificationService.onHubStatusUpdate(saved, next);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> completeHubDelivery(Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            if (!receiverCollectsAtDestinationHub(order)) {
                throw new RuntimeException(
                        "complete-hub-delivery applies only when the receiver collects at the destination hub (DOOR_TO_HUB)");
            }
            if (!OrderStatus.AT_DESTINATION_HUB.equals(order.getStatus())) {
                throw new RuntimeException("Order must be AT_DESTINATION_HUB");
            }
            order.setStatus(OrderStatus.DELIVERED_AT_HUB);
            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Marked delivered at hub");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            try {
                orderNotificationService.onDeliveredAtHub(saved);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> markReadyForDelivery(Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            if (!hasLastMileDoorDelivery(order)) {
                throw new RuntimeException(
                        "ready-for-delivery applies only when the parcel is delivered to the receiver address (DOOR_TO_DOOR or HUB_TO_DOOR)");
            }
            if (!OrderStatus.AT_DESTINATION_HUB.equals(order.getStatus())) {
                throw new RuntimeException("Order must be AT_DESTINATION_HUB");
            }
            order.setStatus(OrderStatus.READY_FOR_DELIVERY);
            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Order ready for delivery");
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
    public ApiResponse<OrderResponseDTO> assignDeliveryRider(Long orderId, Long riderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("riderId is required");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            if (!OrderStatus.READY_FOR_DELIVERY.equals(order.getStatus())) {
                throw new RuntimeException("Order must be READY_FOR_DELIVERY");
            }
            if (Objects.equals(order.getRiderId(), riderId)) {
                throw new RuntimeException("Use a different rider than the pickup rider");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));
            assertRiderAssignable(rider);
            order.setDeliveryRiderId(riderId);
            order.setStatus(OrderStatus.ASSIGNED_TO_DELIVERY_RIDER);
            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Delivery rider assigned");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            try {
                orderNotificationService.onDeliveryRiderAssigned(saved);
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<OrderResponseDTO>> listOrdersForRider(Long riderId) {
        ApiResponse<List<OrderResponseDTO>> response = new ApiResponse<>();
        try {
            Map<Long, OrderEntity> byId = new LinkedHashMap<>();
            for (OrderEntity o : orderRepository.findByRiderIdOrderByUpdatedAtDesc(riderId)) {
                byId.put(o.getId(), o);
            }
            for (OrderEntity o : orderRepository.findByDeliveryRiderIdOrderByUpdatedAtDesc(riderId)) {
                byId.putIfAbsent(o.getId(), o);
            }
            List<OrderEntity> merged = new ArrayList<>(byId.values());
            merged.sort(Comparator.comparing(OrderEntity::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
            List<OrderResponseDTO> dtos = merged.stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Rider orders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
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
    public ApiResponse<OrderResponseDTO> riderAcceptOrder(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            Objects.requireNonNull(riderId, "riderId");
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            if (riderId.equals(order.getRiderId()) && OrderStatus.ASSIGNED.equals(order.getStatus())) {
                order.setStatus(OrderStatus.ACCEPTED);
            } else if (riderId.equals(order.getDeliveryRiderId()) && OrderStatus.ASSIGNED_TO_DELIVERY_RIDER.equals(order.getStatus())) {
                order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            } else {
                throw new RuntimeException("Order cannot be accepted for this rider in status " + order.getStatus());
            }
            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Order accepted");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);
            try {
                if (OrderStatus.OUT_FOR_DELIVERY.equals(saved.getStatus())) {
                    orderNotificationService.onOutForDelivery(saved);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<OrderResponseDTO> riderRejectOrder(Long riderId, Long orderId) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            Objects.requireNonNull(riderId, "riderId");
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));
            if (riderId.equals(order.getRiderId()) && OrderStatus.ASSIGNED.equals(order.getStatus())) {
                order.setRiderId(null);
                order.setStatus(OrderStatus.READY_FOR_ASSIGNMENT);
            } else if (riderId.equals(order.getDeliveryRiderId()) && OrderStatus.ASSIGNED_TO_DELIVERY_RIDER.equals(order.getStatus())) {
                order.setDeliveryRiderId(null);
                order.setStatus(OrderStatus.READY_FOR_DELIVERY);
            } else {
                throw new RuntimeException("Order cannot be rejected for this rider in status " + order.getStatus());
            }
            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Order rejected");
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
    public ApiResponse<OrderResponseDTO> riderUpdateOrderStatus(Long riderId, Long orderId, String status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            Objects.requireNonNull(riderId, "riderId");
            if (status == null || status.isBlank()) {
                throw new RuntimeException("status is required");
            }
            String next = status.trim().toUpperCase();
            if ("PICKED".equalsIgnoreCase(next)) {
                next = OrderStatus.PICKED_UP;
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(orderId))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + orderId));

            if (riderId.equals(order.getRiderId())) {
                applyPickupRiderStatusTransition(order, next);
            } else if (riderId.equals(order.getDeliveryRiderId())) {
                applyDeliveryRiderStatusTransition(order, next);
            } else {
                throw new RuntimeException("Rider is not assigned to this order");
            }

            OrderEntity saved = orderRepository.save(order);
            response.setData(mapToResponseDTO(saved));
            response.setMessage("Order status updated");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            try {
                if (OrderStatus.PICKED_UP.equals(saved.getStatus())) {
                    orderNotificationService.onPickedUp(saved);
                }
                if (OrderStatus.OUT_FOR_DELIVERY.equals(saved.getStatus())) {
                    orderNotificationService.onOutForDelivery(saved);
                }
                if (OrderStatus.DELIVERED.equals(saved.getStatus())) {
                    orderNotificationService.onDelivered(saved);
                }
            } catch (Exception ignored) {
            }
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    private void applyPickupRiderStatusTransition(OrderEntity order, String next) {
        String cur = order.getStatus();
        if (isIncityOrder(order)) {
            if (OrderStatus.ACCEPTED.equals(cur) && OrderStatus.PICKED_UP.equals(next)) {
                order.setStatus(OrderStatus.PICKED_UP);
                return;
            }
            if (OrderStatus.PICKED_UP.equals(cur) && OrderStatus.IN_TRANSIT.equals(next)) {
                order.setStatus(OrderStatus.IN_TRANSIT);
                return;
            }
            if (OrderStatus.IN_TRANSIT.equals(cur) && OrderStatus.DELIVERED.equals(next)) {
                order.setStatus(OrderStatus.DELIVERED);
                return;
            }
            if (OrderStatus.ASSIGNED.equals(cur) && OrderStatus.ACCEPTED.equals(next)) {
                order.setStatus(OrderStatus.ACCEPTED);
                return;
            }
        } else {
            if (OrderStatus.ASSIGNED.equals(cur) && OrderStatus.ACCEPTED.equals(next)) {
                order.setStatus(OrderStatus.ACCEPTED);
                return;
            }
            if (OrderStatus.ACCEPTED.equals(cur) && OrderStatus.PICKED_UP.equals(next)) {
                order.setStatus(OrderStatus.PICKED_UP);
                return;
            }
        }
        throw new RuntimeException("Invalid pickup status transition from " + cur + " to " + next);
    }

    private void applyDeliveryRiderStatusTransition(OrderEntity order, String next) {
        String cur = order.getStatus();
        if (OrderStatus.ASSIGNED_TO_DELIVERY_RIDER.equals(cur) && OrderStatus.OUT_FOR_DELIVERY.equals(next)) {
            order.setStatus(OrderStatus.OUT_FOR_DELIVERY);
            return;
        }
        if (OrderStatus.OUT_FOR_DELIVERY.equals(cur) && OrderStatus.DELIVERED.equals(next)) {
            order.setStatus(OrderStatus.DELIVERED);
            return;
        }
        throw new RuntimeException("Invalid delivery status transition from " + cur + " to " + next);
    }

    private boolean isAssignmentPaymentOk(OrderEntity order) {
        if (order.getPaymentType() != null && order.getPaymentType().trim().equalsIgnoreCase("COD")) {
            return true;
        }
        return "PAID".equalsIgnoreCase(order.getPaymentStatus());
    }

    private boolean isRiderBusy(Long riderId) {
        List<String> pickupBusy = List.of(OrderStatus.ASSIGNED, OrderStatus.ACCEPTED, OrderStatus.PICKED_UP, OrderStatus.IN_TRANSIT);
        List<String> deliveryBusy = List.of(OrderStatus.ASSIGNED_TO_DELIVERY_RIDER, OrderStatus.OUT_FOR_DELIVERY);
        return orderRepository.existsByRiderIdAndStatusIn(riderId, pickupBusy)
                || orderRepository.existsByDeliveryRiderIdAndStatusIn(riderId, deliveryBusy);
    }

    private boolean isIncityOrder(OrderEntity o) {
        if (FulfillmentType.INCITY.equals(o.getFulfillmentType())) {
            return true;
        }
        if (o.getFulfillmentType() != null) {
            return false;
        }
        return o.getVehicleTypeId() != null;
    }

    /** Receiver picks up at destination hub (no last-mile to door). */
    private boolean receiverCollectsAtDestinationHub(OrderEntity o) {
        String ft = o.getFulfillmentType();
        if (ft == null) {
            return false;
        }
        return FulfillmentType.DOOR_TO_HUB.equals(ft) || FulfillmentType.HUB_TO_HUB.equals(ft);
    }

    /** Parcel is delivered to the receiver's door (last-mile from destination hub). */
    private boolean hasLastMileDoorDelivery(OrderEntity o) {
        String ft = o.getFulfillmentType();
        if (FulfillmentType.DOOR_TO_DOOR.equals(ft) || FulfillmentType.HUB_TO_DOOR.equals(ft)) {
            return true;
        }
        return ft == null && !isIncityOrder(o);
    }

    private void assertRiderAssignable(RiderEntity rider) {
        if (!Boolean.TRUE.equals(rider.getIsAvailable())) {
            throw new RuntimeException("Rider is not available");
        }
        String ap = rider.getApprovalStatus();
        if (RiderApprovalStatus.PENDING.equalsIgnoreCase(ap == null ? "" : ap)) {
            throw new RuntimeException("Rider is not approved yet");
        }
        if (RiderApprovalStatus.REJECTED.equalsIgnoreCase(ap == null ? "" : ap)) {
            throw new RuntimeException("Rider is rejected");
        }
        if (isRiderBusy(rider.getId())) {
            throw new RuntimeException("Rider is busy on another order");
        }
    }

    private void validateLatLng(Double lat, Double lng, String label) {
        if (lat == null || lng == null) {
            throw new RuntimeException(label + " coordinates cannot be null");
        }
        if (lat < -90 || lat > 90) {
            throw new RuntimeException(label + "Lat must be between -90 and 90");
        }
        if (lng < -180 || lng > 180) {
            throw new RuntimeException(label + "Lng must be between -180 and 180");
        }
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }
}

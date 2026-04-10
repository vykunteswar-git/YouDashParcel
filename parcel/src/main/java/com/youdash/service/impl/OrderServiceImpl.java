package com.youdash.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderRequestDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.PackageCategoryEntity;
import com.youdash.entity.PackageItemEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.UserEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.entity.GstConfigEntity;
import com.youdash.entity.PlatformFeeEntity;
import com.youdash.entity.DeliveryTypeRateEntity;
import java.util.Objects;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.PackageCategoryRepository;
import com.youdash.repository.PackageItemRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.UserRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.repository.DeliveryTypeRepository;
import com.youdash.repository.DeliveryTypeRateRepository;
import com.youdash.repository.GstConfigRepository;
import com.youdash.repository.PlatformFeeRepository;
import com.youdash.service.NotificationService;
import com.youdash.service.OrderService;
import com.youdash.notification.NotificationType;
import com.youdash.pricing.PricingBreakdown;
import com.youdash.service.DistanceService;
import com.youdash.service.PricingService;
import com.youdash.service.ScopeResolverService;
import com.youdash.pricing.DeliveryScope;

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
    private NotificationService notificationService;

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private GstConfigRepository gstConfigRepository;

    @Autowired
    private PlatformFeeRepository platformFeeRepository;

    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;

    @Autowired
    private DeliveryTypeRateRepository deliveryTypeRateRepository;

    @Autowired
    private ScopeResolverService scopeResolverService;

    @Override
    public ApiResponse<OrderResponseDTO> createOrder(OrderRequestDTO dto) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (dto.getUserId() == null) {
                throw new RuntimeException("UserId is required");
            }

            if (dto.getVehicleTypeId() == null) {
                throw new RuntimeException("vehicleTypeId is required");
            }

            if (dto.getPackageCategoryId() == null) {
                throw new RuntimeException("packageCategoryId is required");
            }

            // Geo validation (optional for backward compatibility)
            boolean anyGeoProvided = dto.getPickupLat() != null || dto.getPickupLng() != null
                    || dto.getDeliveryLat() != null || dto.getDeliveryLng() != null;
            if (anyGeoProvided) {
                if (dto.getPickupLat() == null || dto.getPickupLng() == null || dto.getDeliveryLat() == null || dto.getDeliveryLng() == null) {
                    throw new RuntimeException("pickupLat, pickupLng, deliveryLat, deliveryLng are required when using geo coordinates");
                }
                validateLatLng(dto.getPickupLat(), dto.getPickupLng(), "pickup");
                validateLatLng(dto.getDeliveryLat(), dto.getDeliveryLng(), "delivery");
            }

            // 1. Validate Vehicle
            VehicleEntity vehicle = vehicleRepository.findById(Objects.requireNonNull(dto.getVehicleTypeId()))
                    .orElseThrow(() -> new RuntimeException("Invalid vehicleTypeId: " + dto.getVehicleTypeId()));
            
            if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
                throw new RuntimeException("Selected vehicle is not active");
            }

            // 2. Price Null Safety
            if (vehicle.getPricePerKm() == null) {
                throw new RuntimeException("Vehicle price not configured");
            }

            // Fetch user (for sender defaults)
            UserEntity user = userRepository.findById(dto.getUserId())
                    .filter(u -> Boolean.TRUE.equals(u.getActive()))
                    .orElseThrow(() -> new RuntimeException("Invalid userId: " + dto.getUserId()));

            // 3. Validate Package Category
            PackageCategoryEntity category = packageCategoryRepository.findById(Objects.requireNonNull(dto.getPackageCategoryId()))
                    .orElseThrow(() -> new RuntimeException("Invalid packageCategoryId: " + dto.getPackageCategoryId()));

            if (!Boolean.TRUE.equals(category.getIsActive())) {
                throw new RuntimeException("Selected package category is not active");
            }

            // 4. Determine distance (do not trust client when geo present)
            Double resolvedDistanceKm;
            if (anyGeoProvided) {
                resolvedDistanceKm = distanceService.calculateDistanceKm(
                        dto.getPickupLat(), dto.getPickupLng(), dto.getDeliveryLat(), dto.getDeliveryLng()
                );
            } else {
                if (dto.getDistanceKm() == null || dto.getDistanceKm() <= 0) {
                    throw new RuntimeException("Distance must be greater than 0");
                }
                resolvedDistanceKm = dto.getDistanceKm();
            }
            if (resolvedDistanceKm == null || resolvedDistanceKm <= 0) {
                throw new RuntimeException("Resolved distance must be greater than 0");
            }

            if (dto.getWeight() != null && vehicle.getMaxWeight() != null && dto.getWeight() > vehicle.getMaxWeight()) {
                throw new RuntimeException("Weight exceeds the selected vehicle's maximum capacity (" + vehicle.getMaxWeight() + "kg)");
            }

            // 5. Resolve sender details (phone defaults to user's phone)
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

            // 6. Validate delivery type (default STANDARD for backward compatibility)
            String deliveryType = dto.getDeliveryType();
            if (deliveryType == null || deliveryType.trim().isEmpty()) {
                deliveryType = "STANDARD";
            }
            final String deliveryTypeNormalized = deliveryType.trim();
            DeliveryTypeEntity deliveryTypeEntity = deliveryTypeRepository
                    .findByNameIgnoreCaseAndActiveTrue(deliveryTypeNormalized)
                    .orElseThrow(() -> new RuntimeException("Invalid deliveryType: " + deliveryTypeNormalized));

            DeliveryScope scope = scopeResolverService.resolveScope(resolvedDistanceKm);
            DeliveryTypeRateEntity rate = deliveryTypeRateRepository
                    .findByDeliveryTypeAndScopeAndActiveTrue(deliveryTypeEntity, scope)
                    .orElseThrow(() -> new RuntimeException(
                            "Delivery type rate not configured for "
                                    + deliveryTypeEntity.getName()
                                    + " / "
                                    + scope.name()
                    ));

            // 7. Fetch pricing configs (DB driven)
            GstConfigEntity gstCfg = gstConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                    .orElseThrow(() -> new RuntimeException("GST config not found"));
            PlatformFeeEntity platformCfg = platformFeeRepository.findFirstByActiveTrueOrderByIdDesc()
                    .orElseThrow(() -> new RuntimeException("Platform fee config not found"));

            java.math.BigDecimal distanceBd = java.math.BigDecimal.valueOf(resolvedDistanceKm);
            java.math.BigDecimal pricePerKmBd = java.math.BigDecimal.valueOf(vehicle.getPricePerKm());
            java.math.BigDecimal gstPercent = gstCfg.getGstPercent();
            if (gstPercent == null) {
                gstPercent = nz(gstCfg.getCgstPercent()).add(nz(gstCfg.getSgstPercent()));
            }

            PricingBreakdown pricing = pricingService.calculate(
                    distanceBd,
                    pricePerKmBd,
                    rate.getFee(),
                    platformCfg.getFee(),
                    gstPercent
            );

            OrderEntity order = new OrderEntity();
            
            // Mapping from DTO to Entity
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
            
            // Description Auto-fill
            if (dto.getDescription() == null || dto.getDescription().trim().isEmpty()) {
                order.setDescription(category.getDefaultDescription());
            } else {
                order.setDescription(dto.getDescription());
            }

            order.setWeight(dto.getWeight());
            order.setImageUrl(dto.getImageUrl());
            order.setVehicleTypeId(dto.getVehicleTypeId());
            order.setDistanceKm(resolvedDistanceKm);

            // Pricing breakdown + snapshots
            order.setBaseAmount(pricing.getBase());
            order.setPlatformFee(pricing.getPlatformFee());
            // Legacy split fields kept populated for compatibility.
            java.math.BigDecimal halfGstAmount = nz(pricing.getGst()).divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            order.setCgstAmount(halfGstAmount);
            order.setSgstAmount(halfGstAmount);
            order.setTotalAmount(pricing.getTotal().doubleValue()); // keep legacy field populated

            order.setDeliveryTypeUsed(deliveryTypeEntity.getName());
            order.setDeliveryTypeScopeUsed(scope.name());
            order.setDeliveryTypeDescriptionUsed(rate.getDescription());
            order.setDeliveryTypeFeeUsed(rate.getFee());
            order.setPricePerKmUsed(pricePerKmBd.setScale(2, java.math.RoundingMode.HALF_UP));
            java.math.BigDecimal halfGstPercent = nz(gstPercent).divide(java.math.BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
            order.setCgstPercentUsed(halfGstPercent);
            order.setSgstPercentUsed(halfGstPercent);

            order.setPaymentType(dto.getPaymentType());
            order.setScheduledDate(dto.getScheduledDate());
            order.setTimeSlot(dto.getTimeSlot());
            
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
            order.setStatus("CREATED");
            order.setPaymentStatus("PENDING");

            OrderEntity savedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(savedOrder));
            response.setMessage("Order created successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            try {
                userRepository.findById(savedOrder.getUserId())
                        .filter(u -> Boolean.TRUE.equals(u.getActive()))
                        .map(UserEntity::getFcmToken)
                        .ifPresent(token -> notificationService.sendNotification(
                                token,
                                "Order Created",
                                "Your order has been created successfully.",
                                savedOrder.getId(),
                                NotificationType.ORDER_CREATED
                        ));
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
    public ApiResponse<OrderResponseDTO> updateOrderStatus(Long id, String status) {
        ApiResponse<OrderResponseDTO> response = new ApiResponse<>();
        try {
            if (id == null) {
                throw new RuntimeException("Order ID cannot be null");
            }
            OrderEntity order = orderRepository.findById(Objects.requireNonNull(id))
                    .orElseThrow(() -> new RuntimeException("Order not found with id: " + id));

            // Status Validation
            List<String> allowedStatuses = List.of("CREATED", "ASSIGNED", "PICKED", "IN_TRANSIT", "DELIVERED", "CANCELLED");
            if (!allowedStatuses.contains(status)) {
                throw new RuntimeException("Invalid status");
            }

            String previousStatus = order.getStatus();
            order.setStatus(status);
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Order status updated successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            if ("DELIVERED".equals(status) && (previousStatus == null || !"DELIVERED".equals(previousStatus))) {
                try {
                    userRepository.findById(updatedOrder.getUserId())
                            .filter(u -> Boolean.TRUE.equals(u.getActive()))
                            .map(UserEntity::getFcmToken)
                            .ifPresent(token -> notificationService.sendNotification(
                                    token,
                                    "Delivered",
                                    "Your order has been delivered.",
                                    updatedOrder.getId(),
                                    NotificationType.DELIVERED
                            ));
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

            if (!"CREATED".equals(order.getStatus())) {
                throw new RuntimeException("Rider can only be assigned when order is CREATED");
            }

            order.setRiderId(riderId);
            order.setStatus("ASSIGNED");
            OrderEntity updatedOrder = orderRepository.save(order);

            response.setData(mapToResponseDTO(updatedOrder));
            response.setMessage("Rider assigned successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            try {
                userRepository.findById(updatedOrder.getUserId())
                        .filter(u -> Boolean.TRUE.equals(u.getActive()))
                        .map(UserEntity::getFcmToken)
                        .ifPresent(token -> notificationService.sendNotification(
                                token,
                                "Rider Assigned",
                                "A rider has been assigned to your order.",
                                updatedOrder.getId(),
                                NotificationType.RIDER_ASSIGNED
                        ));
            } catch (Exception ignored) {
                // Never fail API for notification issues
            }

            try {
                riderRepository.findById(Objects.requireNonNull(riderId))
                        .map(RiderEntity::getFcmToken)
                        .ifPresent(token -> notificationService.sendNotification(
                                token,
                                "New Delivery",
                                "You have been assigned a new delivery.",
                                updatedOrder.getId(),
                                NotificationType.RIDER_ASSIGNED
                        ));
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

            if ("DELIVERED".equals(order.getStatus()) || "IN_TRANSIT".equals(order.getStatus())) {
                throw new RuntimeException("Cannot cancel order in " + order.getStatus() + " status");
            }

            order.setStatus("CANCELLED");
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

            if (!"CREATED".equals(order.getStatus())) {
                throw new RuntimeException("Order can only be updated in CREATED status");
            }

            // Update allowed fields
            if (dto.getPickupAddress() != null) order.setPickupAddress(dto.getPickupAddress());
            if (dto.getDeliveryAddress() != null) order.setDeliveryAddress(dto.getDeliveryAddress());
            if (dto.getDescription() != null) order.setDescription(dto.getDescription());
            
            // Weight re-validation if updated
            if (dto.getWeight() != null) {
                VehicleEntity vehicle = vehicleRepository.findById(Objects.requireNonNull(order.getVehicleTypeId()))
                        .orElseThrow(() -> new RuntimeException("Vehicle not found"));
                if (vehicle.getMaxWeight() != null && dto.getWeight() > vehicle.getMaxWeight()) {
                     throw new RuntimeException("Weight exceeds vehicle capacity");
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
        dto.setDeliveryTypeUsed(order.getDeliveryTypeUsed());
        dto.setDeliveryTypeScopeUsed(order.getDeliveryTypeScopeUsed());
        dto.setDeliveryTypeDescriptionUsed(order.getDeliveryTypeDescriptionUsed());
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
        dto.setCgstAmount(order.getCgstAmount() == null ? null : order.getCgstAmount().doubleValue());
        dto.setSgstAmount(order.getSgstAmount() == null ? null : order.getSgstAmount().doubleValue());
        dto.setPricePerKmUsed(order.getPricePerKmUsed() == null ? null : order.getPricePerKmUsed().doubleValue());
        dto.setCgstPercentUsed(order.getCgstPercentUsed() == null ? null : order.getCgstPercentUsed().doubleValue());
        dto.setSgstPercentUsed(order.getSgstPercentUsed() == null ? null : order.getSgstPercentUsed().doubleValue());
        dto.setPaymentType(order.getPaymentType());
        dto.setPaymentStatus(order.getPaymentStatus());
        dto.setStatus(order.getStatus());
        dto.setRiderId(order.getRiderId());
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

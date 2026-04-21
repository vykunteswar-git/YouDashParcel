package com.youdash.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FcmTokenRequestDTO;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RiderSelfUpdateDTO;
import com.youdash.dto.rating.RiderRatingSummaryDTO;
import com.youdash.entity.RiderEntity;
import com.youdash.exception.RateLimitException;
import com.youdash.repository.RiderRepository;
import com.youdash.security.RiderAccessVerifier;
import com.youdash.service.LocationUpdateRateLimiter;
import com.youdash.service.OrderService;
import com.youdash.service.RiderService;
import com.youdash.service.RiderOrderService;
import com.youdash.service.RiderRatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/riders")
@Tag(name = "Riders — App", description = "Rider app APIs: profile, availability, live location, and INCITY accept/reject flow.")
public class RiderController {

    @Autowired
    private RiderService riderService;

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private RiderAccessVerifier riderAccessVerifier;

    @Autowired
    private RiderOrderService riderOrderService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private LocationUpdateRateLimiter locationRateLimiter;

    @Autowired
    private RiderRatingService riderRatingService;

    @PostMapping("/fcm-token")
    public ApiResponse<String> saveFcmToken(@RequestBody FcmTokenRequestDTO dto, HttpServletRequest request) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            Long riderId = riderAccessVerifier.resolveActingRiderId(request);
            if (dto == null || dto.getToken() == null || dto.getToken().trim().isEmpty()) {
                throw new RuntimeException("FCM token is required");
            }

            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found"));

            rider.setFcmToken(dto.getToken().trim());
            riderRepository.save(rider);

            response.setData("Token saved");
            response.setMessage("FCM token saved successfully");
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

    @PostMapping
    public ApiResponse<RiderResponseDTO> createRider(@RequestBody RiderRequestDTO dto) {
        return riderService.createRider(dto);
    }

    @GetMapping
    public ApiResponse<List<RiderResponseDTO>> getAllRiders() {
        return riderService.getAllRiders();
    }

    @GetMapping("/available")
    public ApiResponse<List<RiderResponseDTO>> getAvailableRiders() {
        return riderService.getAvailableRiders();
    }

    @GetMapping("/me/orders/{orderId}")
    @Operation(summary = "Get order by id (my assignment)", description = "Same as GET /rider/orders/{orderId}: order must be assigned to this rider.")
    public ApiResponse<OrderResponseDTO> getMyOrderById(
            @PathVariable Long orderId,
            HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return orderService.getOrder(orderId, riderId, "RIDER", false);
    }

    @GetMapping("/me")
    @Operation(summary = "Get my rider profile (JWT)")
    public ApiResponse<RiderResponseDTO> myProfile(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        RiderEntity rider = riderRepository.findById(riderId)
                .orElseThrow(() -> new RuntimeException("Rider not found"));
        return riderService.getRiderProfile(rider);
    }

    @GetMapping("/me/ratings")
    @Operation(summary = "Get my rating summary (JWT)")
    public ApiResponse<RiderRatingSummaryDTO> myRatings(HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderRatingService.getRiderRatingSummary(riderId);
    }

    @PatchMapping("/me")
    public ApiResponse<RiderResponseDTO> patchMyProfile(@RequestBody RiderSelfUpdateDTO dto,
            HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderService.patchSelfProfile(riderId, dto);
    }

    @PutMapping("/me/availability")
    @Operation(summary = "Update my availability (JWT)")
    public ApiResponse<RiderResponseDTO> updateMyAvailability(
            @RequestBody Map<String, Boolean> statusMap,
            HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        Boolean isAvailable = statusMap.get("isAvailable");
        if (isAvailable == null) {
            throw new RuntimeException("isAvailable is required");
        }
        return riderService.updateAvailability(riderId, isAvailable);
    }

    @PutMapping("/me/location")
    @Operation(summary = "Update my location (JWT)", description = "Also publishes live location to order subscribers for active INCITY orders. Rate limited to 1 update per 3s per rider.")
    public ApiResponse<RiderResponseDTO> updateMyLocation(
            @RequestBody Map<String, Double> locationMap,
            HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        if (!locationRateLimiter.tryAcquire(riderId)) {
            throw new RateLimitException("Location update rate limit exceeded. Max 1 update per 3 seconds.");
        }
        Double lat = locationMap.get("lat");
        Double lng = locationMap.get("lng");
        if (lat == null || lng == null) {
            throw new RuntimeException("lat and lng are required");
        }
        return riderService.updateLocation(riderId, lat, lng);
    }

    @PutMapping("/{id}/availability")
    public ApiResponse<RiderResponseDTO> updateAvailability(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> statusMap,
            HttpServletRequest request) {
        if (!riderAccessVerifier.canAccessRider(request, id)) {
            throw new RuntimeException("Access denied");
        }
        Boolean isAvailable = statusMap.get("isAvailable");
        if (isAvailable == null) {
            throw new RuntimeException("isAvailable is required");
        }
        return riderService.updateAvailability(id, isAvailable);
    }

    @PutMapping("/{id}/location")
    public ApiResponse<RiderResponseDTO> updateLocation(
            @PathVariable Long id,
            @RequestBody Map<String, Double> locationMap,
            HttpServletRequest request) {
        if (!riderAccessVerifier.canAccessRider(request, id)) {
            throw new RuntimeException("Access denied");
        }
        Double lat = locationMap.get("lat");
        Double lng = locationMap.get("lng");
        if (lat == null || lng == null) {
            throw new RuntimeException("lat and lng are required");
        }
        return riderService.updateLocation(id, lat, lng);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get order by id (assigned to me)", description = "Same as GET /riders/me/orders/{orderId} and GET /rider/orders/{orderId}. Use this path if /me/orders returns 403 (same prefix as accept/pickup).")
    public ApiResponse<OrderResponseDTO> getOrderByIdForRider(
            @PathVariable Long orderId,
            HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return orderService.getOrder(orderId, riderId, "RIDER", false);
    }

    @PostMapping("/orders/{orderId}/accept")
    @Operation(summary = "Accept INCITY order request (JWT)", description = "Locks rider immediately and sets order to RIDER_ACCEPTED with 60s payment window.")
    public ApiResponse<?> acceptOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderOrderService.accept(riderId, orderId);
    }

    @PostMapping("/orders/{orderId}/reject")
    @Operation(summary = "Reject INCITY order request (JWT)", description = "Only allowed if rider was in dispatched list for that order.")
    public ApiResponse<?> rejectOrder(@PathVariable Long orderId, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderOrderService.reject(riderId, orderId);
    }

    @PutMapping("/orders/{orderId}/pickup")
    @Operation(summary = "Mark parcel picked up (CONFIRMED → PICKED_UP)")
    public ApiResponse<?> markPickedUp(@PathVariable Long orderId, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderOrderService.markPickedUp(riderId, orderId);
    }

    @PutMapping("/orders/{orderId}/start-transit")
    @Operation(summary = "Start transit (PICKED_UP → IN_TRANSIT)")
    public ApiResponse<?> startTransit(@PathVariable Long orderId, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderOrderService.startTransit(riderId, orderId);
    }

    @PutMapping("/orders/{orderId}/reach-destination")
    @Operation(summary = "Reach destination (logging only; status remains IN_TRANSIT)")
    public ApiResponse<?> reachDestination(@PathVariable Long orderId, HttpServletRequest request) {
        Long riderId = riderAccessVerifier.resolveActingRiderId(request);
        return riderOrderService.reachDestination(riderId, orderId);
    }
}

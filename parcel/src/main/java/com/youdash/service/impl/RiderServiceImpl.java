package com.youdash.service.impl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.security.SecureRandom;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.OrderResponseDTO;
import com.youdash.dto.RiderOnlineTimeDTO;
import com.youdash.dto.RiderRequestDTO;
import com.youdash.dto.RiderResponseDTO;
import com.youdash.dto.RiderSelfUpdateDTO;
import com.youdash.dto.wallet.RiderWalletTransactionDTO;
import com.youdash.dto.wallet.RiderWithdrawalDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.RiderLocationHistoryEntity;
import com.youdash.entity.RiderOnlineSessionEntity;
import com.youdash.entity.VehicleEntity;
import com.youdash.entity.wallet.RiderWalletEntity;
import com.youdash.entity.wallet.RiderWalletTransactionEntity;
import com.youdash.entity.wallet.RiderWithdrawalEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.RiderApprovalStatus;
import com.youdash.model.ServiceMode;
import com.youdash.notification.NotificationType;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderLocationHistoryRepository;
import com.youdash.repository.RiderOnlineSessionRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.repository.VehicleRepository;
import com.youdash.repository.wallet.RiderWalletRepository;
import com.youdash.repository.wallet.RiderWalletTransactionRepository;
import com.youdash.repository.wallet.RiderWithdrawalRepository;
import com.youdash.service.NotificationDedupService;
import com.youdash.service.NotificationService;
import com.youdash.service.RiderService;
import com.youdash.realtime.UserActiveOrderTopicPublisher;
import com.youdash.util.GeoUtils;
import com.youdash.util.JwtUtil;
import com.youdash.dto.realtime.RiderLocationEventDTO;

import org.springframework.messaging.simp.SimpMessagingTemplate;

@Service
public class RiderServiceImpl implements RiderService {

    private static final SecureRandom RIDER_ID_RANDOM = new SecureRandom();

    @Autowired
    private RiderRepository riderRepository;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderWalletRepository riderWalletRepository;

    @Autowired
    private RiderWalletTransactionRepository riderWalletTransactionRepository;

    @Autowired
    private RiderWithdrawalRepository riderWithdrawalRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RiderLocationHistoryRepository locationHistoryRepository;

    @Autowired
    private RiderOnlineSessionRepository riderOnlineSessionRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDedupService notificationDedupService;

    @Autowired
    private UserActiveOrderTopicPublisher userActiveOrderTopicPublisher;

    @Value("${youdash.tracking.city-speed-kmh:20}")
    private double citySpeedKmh;

    @Value("${youdash.tracking.near-destination-radius-m:300}")
    private double nearDestinationRadiusM;

    @Value("${youdash.tracking.at-destination-radius-m:50}")
    private double atDestinationRadiusM;

    @Value("${youdash.reporting.zone:Asia/Kolkata}")
    private String reportingZone;

    @Override
    public ApiResponse<RiderResponseDTO> createRider(RiderRequestDTO dto) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (dto.getName() == null || dto.getName().isEmpty()) {
                throw new RuntimeException("Rider name is required");
            }
            if (dto.getPhone() == null || dto.getPhone().isEmpty()) {
                throw new RuntimeException("Rider phone is required");
            }
            String phone = dto.getPhone().trim();
            if (phone.length() < 10) {
                throw new RuntimeException("Invalid phone number");
            }
            if (riderRepository.findByPhone(phone).isPresent()) {
                throw new RuntimeException("A rider is already registered with this phone number");
            }
            String email = normalizeEmail(dto.getEmail());
            if (email != null) {
                if (!isValidEmail(email)) {
                    throw new RuntimeException("Invalid email format");
                }
                if (riderRepository.findByEmailIgnoreCase(email).isPresent()) {
                    throw new RuntimeException("A rider is already registered with this email");
                }
            }
            if (dto.getEmergencyPhone() == null || dto.getEmergencyPhone().trim().isEmpty()) {
                throw new RuntimeException("Emergency phone is required");
            }
            if (dto.getEmergencyPhone().trim().length() < 10) {
                throw new RuntimeException("Invalid emergency phone number");
            }
            if (dto.getProfileImageUrl() == null || dto.getProfileImageUrl().trim().isEmpty()) {
                throw new RuntimeException("Profile image is required");
            }
            if (dto.getVehicleId() == null && (dto.getVehicleType() == null || dto.getVehicleType().trim().isEmpty())) {
                throw new RuntimeException("Vehicle is required");
            }
            if (dto.getVehicleNumber() == null || dto.getVehicleNumber().trim().isEmpty()) {
                throw new RuntimeException("Vehicle number is required");
            }
            String vehicleNumber = dto.getVehicleNumber().trim().toUpperCase(Locale.ROOT);
            if (vehicleNumber.length() < 4) {
                throw new RuntimeException("Invalid vehicle number");
            }

            RiderEntity rider = new RiderEntity();
            rider.setName(dto.getName());
            rider.setPhone(phone);
            rider.setEmail(email);
            rider.setPublicId(generateUniquePublicId());

            // Prefer vehicleId (dropdown) -> resolve to vehicle name; fallback to legacy
            // vehicleType string.
            String resolvedVehicleType = null;
            if (dto.getVehicleId() != null) {
                VehicleEntity vehicle = vehicleRepository.findById(dto.getVehicleId())
                        .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + dto.getVehicleId()));
                if (Boolean.FALSE.equals(vehicle.getIsActive())) {
                    throw new RuntimeException("Selected vehicle is not active");
                }
                rider.setVehicleId(vehicle.getId());
                resolvedVehicleType = vehicle.getName();
            } else {
                resolvedVehicleType = dto.getVehicleType().trim();
            }
            rider.setVehicleType(resolvedVehicleType);
            rider.setVehicleNumber(vehicleNumber);
            rider.setEmergencyPhone(dto.getEmergencyPhone().trim());

            rider.setProfileImageUrl(dto.getProfileImageUrl().trim());
            rider.setAadhaarImageUrl(nzTrimToNull(dto.getAadhaarImageUrl()));
            rider.setLicenseImageUrl(nzTrimToNull(dto.getLicenseImageUrl()));

            // Location is not required at registration time; it can be updated later.
            rider.setCurrentLat(0.0);
            rider.setCurrentLng(0.0);

            // Set defaults
            rider.setIsAvailable(true);
            rider.setIsBlocked(false);
            rider.setRating(0.0);

            RiderEntity savedRider = riderRepository.save(rider);

            RiderResponseDTO data = mapToResponseDTO(savedRider);
            data.setToken(jwtUtil.generateToken(savedRider.getId(), "RIDER"));
            response.setData(data);
            response.setMessage("Rider created successfully");
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
    public ApiResponse<RiderResponseDTO> getRiderProfile(RiderEntity rider) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (rider == null || rider.getId() == null) {
                throw new RuntimeException("Rider not found");
            }
            RiderResponseDTO data = mapToResponseDTO(rider, false);
            data.setRiderStatus(computeRiderStatus(rider));
            response.setData(data);
            response.setMessage("Rider profile fetched");
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

    private static String nzTrimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static final java.util.regex.Pattern EMAIL_REGEX = java.util.regex.Pattern
            .compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** Trim + lowercase; blank/null → null. */
    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String t = email.trim();
        if (t.isEmpty()) {
            return null;
        }
        return t.toLowerCase(Locale.ROOT);
    }

    private static boolean isValidEmail(String email) {
        return email != null && EMAIL_REGEX.matcher(email).matches();
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> getAllRiders() {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            List<RiderEntity> riders = riderRepository.findAll();
            List<RiderResponseDTO> dtos = riders.stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());

            response.setData(dtos);
            response.setMessage("Riders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);

        } catch (Exception e) {
            response.setMessage("Failed to fetch riders: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<List<RiderResponseDTO>> getAvailableRiders() {
        return listRidersEligibleForAssignment();
    }

    @Override
    public ApiResponse<RiderResponseDTO> updateAvailability(Long riderId, Boolean status) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));

            if (status == null) {
                throw new RuntimeException("Availability status is required");
            }
            rider.setIsAvailable(status);
            RiderEntity updatedRider = riderRepository.save(rider);
            syncOnlineSession(riderId, status);

            response.setData(mapToResponseDTO(updatedRider));
            response.setMessage("Availability updated successfully");
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
    public ApiResponse<RiderResponseDTO> updateLocation(Long riderId, Double lat, Double lng) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));

            if (lat == null || lng == null) {
                throw new RuntimeException("Location cannot be null");
            }
            rider.setCurrentLat(lat);
            rider.setCurrentLng(lng);
            RiderEntity updatedRider = riderRepository.save(rider);

            response.setData(mapToResponseDTO(updatedRider));
            response.setMessage("Location updated successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setSuccess(true);

            // INCITY only: broadcast + history save + ETA + geofencing.
            try {
                List<OrderEntity> activeIncityOrders = orderRepository.findByRiderIdAndServiceModeAndStatusIn(
                        riderId,
                        ServiceMode.INCITY,
                        List.of(OrderStatus.CONFIRMED, OrderStatus.PICKED_UP, OrderStatus.IN_TRANSIT));

                long ts = System.currentTimeMillis();
                Instant now = Instant.ofEpochMilli(ts);

                for (OrderEntity o : activeIncityOrders) {
                    // 1. Save location history asynchronously (non-blocking).
                    final Long orderId = o.getId();
                    CompletableFuture.runAsync(() -> {
                        RiderLocationHistoryEntity hist = new RiderLocationHistoryEntity();
                        hist.setRiderId(riderId);
                        hist.setOrderId(orderId);
                        hist.setLat(lat);
                        hist.setLng(lng);
                        hist.setTs(now);
                        locationHistoryRepository.save(hist);
                    });

                    // 2. Compute distance and ETA to drop location.
                    double distToDropKm = GeoUtils.haversineKm(lat, lng, o.getDropLat(), o.getDropLng());
                    int etaSeconds = (int) ((distToDropKm / citySpeedKmh) * 3600);

                    // 3. Geofence checks (deduplicated per order).
                    double nearRadiusKm = nearDestinationRadiusM / 1000.0;
                    double atRadiusKm = atDestinationRadiusM / 1000.0;
                    boolean atDest = GeoUtils.isInsideCircle(lat, lng, o.getDropLat(), o.getDropLng(), atRadiusKm);
                    boolean nearDest = atDest
                            || GeoUtils.isInsideCircle(lat, lng, o.getDropLat(), o.getDropLng(), nearRadiusKm);

                    if (atDest && notificationDedupService.tryAcquire("at_dest:" + orderId)) {
                        Map<String, String> data = Map.of("orderId", String.valueOf(orderId), "type",
                                NotificationType.USER_RIDER_AT_DESTINATION.name());
                        notificationService.sendToUser(o.getUserId(), "Rider has arrived!",
                                "Please show the OTP to complete delivery.", data,
                                NotificationType.USER_RIDER_AT_DESTINATION);
                        messagingTemplate.convertAndSend("/topic/users/" + o.getUserId() + "/order-events",
                                Map.of("event", "at_destination", "orderId", orderId));
                    } else if (nearDest && notificationDedupService.tryAcquire("near_dest:" + orderId)) {
                        Map<String, String> data = Map.of("orderId", String.valueOf(orderId), "type",
                                NotificationType.USER_RIDER_NEAR_DESTINATION.name());
                        notificationService.sendToUser(o.getUserId(), "Rider is almost there!",
                                "Your rider is less than 1 minute away.", data,
                                NotificationType.USER_RIDER_NEAR_DESTINATION);
                        messagingTemplate.convertAndSend("/topic/users/" + o.getUserId() + "/order-events",
                                Map.of("event", "near_destination", "orderId", orderId));
                    }

                    // 4. Broadcast enhanced location event.
                    RiderLocationEventDTO evt = new RiderLocationEventDTO();
                    evt.setOrderId(orderId);
                    evt.setRiderId(riderId);
                    evt.setLat(lat);
                    evt.setLng(lng);
                    evt.setTs(ts);
                    evt.setEtaSeconds(etaSeconds);
                    evt.setDistanceToDropKm(Math.round(distToDropKm * 100.0) / 100.0);
                    evt.setNearDestination(nearDest);
                    evt.setAtDestination(atDest);
                    messagingTemplate.convertAndSend("/topic/orders/" + orderId + "/rider-location", evt);
                    userActiveOrderTopicPublisher.publishEtaUpdated(
                            o.getUserId(),
                            orderId,
                            o.getStatus() == null ? null : o.getStatus().name(),
                            riderId,
                            etaSeconds,
                            Math.round(distToDropKm * 100.0) / 100.0);
                }
            } catch (Exception ignored) {
                // Never fail the REST update just because realtime publish failed.
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
    public ApiResponse<RiderResponseDTO> patchSelfProfile(Long riderId, RiderSelfUpdateDTO dto) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            if (dto == null) {
                throw new RuntimeException("Request body is required");
            }
            RiderEntity rider = riderRepository.findById(riderId)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + riderId));

            boolean changed = false;
            if (dto.getIsAvailable() != null) {
                rider.setIsAvailable(dto.getIsAvailable());
                changed = true;
            }
            if (dto.getCurrentLat() != null && dto.getCurrentLng() != null) {
                rider.setCurrentLat(dto.getCurrentLat());
                rider.setCurrentLng(dto.getCurrentLng());
                changed = true;
            } else if (dto.getCurrentLat() != null || dto.getCurrentLng() != null) {
                throw new RuntimeException("currentLat and currentLng must be provided together");
            }
            if (dto.getEmergencyPhone() != null) {
                String ep = dto.getEmergencyPhone().trim();
                if (ep.isEmpty()) {
                    throw new RuntimeException("Emergency phone cannot be empty");
                }
                if (ep.length() < 10) {
                    throw new RuntimeException("Invalid emergency phone number");
                }
                rider.setEmergencyPhone(ep);
                changed = true;
            }
            if (dto.getFcmToken() != null) {
                String fcm = dto.getFcmToken().trim();
                if (fcm.isEmpty()) {
                    throw new RuntimeException("FCM token cannot be empty");
                }
                rider.setFcmToken(fcm);
                changed = true;
            }
            if (dto.getEmail() != null) {
                String newEmail = normalizeEmail(dto.getEmail());
                if (newEmail == null) {
                    rider.setEmail(null);
                } else {
                    if (!isValidEmail(newEmail)) {
                        throw new RuntimeException("Invalid email format");
                    }
                    riderRepository.findByEmailIgnoreCase(newEmail).ifPresent(existing -> {
                        if (!existing.getId().equals(rider.getId())) {
                            throw new RuntimeException("A rider is already registered with this email");
                        }
                    });
                    rider.setEmail(newEmail);
                }
                changed = true;
            }
            if (!changed) {
                throw new RuntimeException("No updatable fields provided");
            }

            RiderEntity updated = riderRepository.save(rider);
            if (dto.getIsAvailable() != null) {
                syncOnlineSession(riderId, dto.getIsAvailable());
            }
            response.setData(mapToResponseDTO(updated));
            response.setMessage("Profile updated successfully");
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
    public ApiResponse<List<RiderResponseDTO>> listPendingRiders() {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            List<RiderResponseDTO> dtos = riderRepository
                    .findByApprovalStatusOrderByCreatedAtDesc(RiderApprovalStatus.PENDING).stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Pending riders fetched successfully");
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
    public ApiResponse<List<RiderResponseDTO>> listByApprovalStatus(String approvalStatus) {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            if (approvalStatus == null || approvalStatus.isBlank()) {
                throw new RuntimeException("status is required");
            }
            List<RiderResponseDTO> dtos = riderRepository
                    .findByApprovalStatusOrderByCreatedAtDesc(approvalStatus.trim()).stream()
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Riders fetched successfully");
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
    public ApiResponse<RiderResponseDTO> approveRider(Long id) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            RiderEntity rider = riderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + id));
            rider.setApprovalStatus(RiderApprovalStatus.APPROVED);
            riderRepository.save(rider);
            response.setData(mapToResponseDTO(rider));
            response.setMessage("Rider approved");
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
    public ApiResponse<RiderResponseDTO> rejectRider(Long id) {
        ApiResponse<RiderResponseDTO> response = new ApiResponse<>();
        try {
            RiderEntity rider = riderRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rider not found with id: " + id));
            rider.setApprovalStatus(RiderApprovalStatus.REJECTED);
            riderRepository.save(rider);
            response.setData(mapToResponseDTO(rider));
            response.setMessage("Rider rejected");
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
    public ApiResponse<List<RiderResponseDTO>> listRidersEligibleForAssignment() {
        ApiResponse<List<RiderResponseDTO>> response = new ApiResponse<>();
        try {
            List<RiderResponseDTO> dtos = riderRepository.findByIsAvailableTrue().stream()
                    .filter(this::isApprovedOrLegacy)
                    .map(this::mapToResponseDTO)
                    .collect(Collectors.toList());
            response.setData(dtos);
            response.setMessage("Eligible riders fetched successfully");
            response.setMessageKey("SUCCESS");
            response.setStatus(200);
            response.setTotalCount(dtos.size());
            response.setSuccess(true);
        } catch (Exception e) {
            response.setMessage("Failed to fetch eligible riders: " + e.getMessage());
            response.setMessageKey("ERROR");
            response.setStatus(500);
            response.setSuccess(false);
        }
        return response;
    }

    @Override
    public ApiResponse<RiderOnlineTimeDTO> getOnlineTimeForDate(Long riderId, String dateIso) {
        ApiResponse<RiderOnlineTimeDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("Rider ID cannot be null");
            }
            ZoneId zone = resolveReportingZone();
            LocalDate day = (dateIso == null || dateIso.isBlank())
                    ? LocalDate.now(zone)
                    : LocalDate.parse(dateIso.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            Instant windowStart = day.atStartOfDay(zone).toInstant();
            Instant windowEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
            List<RiderOnlineSessionEntity> sessions =
                    riderOnlineSessionRepository.findSessionsOverlappingWindow(riderId, windowStart, windowEnd);
            long totalSeconds = 0L;
            for (RiderOnlineSessionEntity s : sessions) {
                Instant start = s.getStartedAt();
                Instant end = s.getEndedAt() == null ? Instant.now() : s.getEndedAt();
                if (start == null || !end.isAfter(start)) {
                    continue;
                }
                Instant overlapStart = start.isAfter(windowStart) ? start : windowStart;
                Instant overlapEnd = end.isBefore(windowEnd) ? end : windowEnd;
                if (overlapEnd.isAfter(overlapStart)) {
                    totalSeconds += overlapEnd.getEpochSecond() - overlapStart.getEpochSecond();
                }
            }
            String formatted = formatDuration(totalSeconds);
            String activeStartedAt = riderOnlineSessionRepository
                    .findFirstByRiderIdAndEndedAtIsNullOrderByStartedAtDesc(riderId)
                    .map(RiderOnlineSessionEntity::getStartedAt)
                    .map(Instant::toString)
                    .orElse(null);
            response.setData(RiderOnlineTimeDTO.builder()
                    .date(day.toString())
                    .totalOnlineSeconds(totalSeconds)
                    .totalOnlineMinutes(totalSeconds / 60L)
                    .formatted(formatted)
                    .activeSessionStartedAt(activeStartedAt)
                    .build());
            response.setMessage("OK");
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

    private void syncOnlineSession(Long riderId, Boolean isAvailable) {
        if (riderId == null || isAvailable == null) {
            return;
        }
        Instant now = Instant.now();
        RiderOnlineSessionEntity active = riderOnlineSessionRepository
                .findFirstByRiderIdAndEndedAtIsNullOrderByStartedAtDesc(riderId)
                .orElse(null);
        if (Boolean.TRUE.equals(isAvailable)) {
            if (active == null) {
                RiderOnlineSessionEntity session = new RiderOnlineSessionEntity();
                session.setRiderId(riderId);
                session.setStartedAt(now);
                riderOnlineSessionRepository.save(session);
            }
            return;
        }
        if (active != null) {
            active.setEndedAt(now);
            riderOnlineSessionRepository.save(active);
        }
    }

    private ZoneId resolveReportingZone() {
        try {
            return ZoneId.of(reportingZone);
        } catch (Exception ignored) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    private static String formatDuration(long totalSeconds) {
        long h = totalSeconds / 3600L;
        long m = (totalSeconds % 3600L) / 60L;
        long s = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s);
    }

    private boolean isApprovedOrLegacy(RiderEntity rider) {
        String ap = rider.getApprovalStatus();
        if (ap == null || ap.isBlank()) {
            return true;
        }
        return RiderApprovalStatus.APPROVED.equalsIgnoreCase(ap);
    }

    private RiderResponseDTO mapToResponseDTO(RiderEntity rider) {
        return mapToResponseDTO(rider, true);
    }

    private RiderResponseDTO mapToResponseDTO(RiderEntity rider, boolean includeRecentOrders) {
        RiderResponseDTO dto = new RiderResponseDTO();
        dto.setId(rider.getId());
        dto.setPublicId(rider.getPublicId());
        dto.setName(rider.getName());
        dto.setPhone(rider.getPhone());
        dto.setEmail(rider.getEmail());
        dto.setVehicleId(rider.getVehicleId());
        dto.setVehicleType(rider.getVehicleType());
        dto.setVehicleNumber(rider.getVehicleNumber());
        dto.setIsAvailable(rider.getIsAvailable());
        dto.setIsBlocked(rider.getIsBlocked());
        dto.setRating(rider.getRating());
        dto.setApprovalStatus(rider.getApprovalStatus());
        dto.setEmergencyPhone(rider.getEmergencyPhone());
        dto.setCurrentLat(rider.getCurrentLat());
        dto.setCurrentLng(rider.getCurrentLng());
        dto.setFcmToken(rider.getFcmToken());
        dto.setProfileImageUrl(rider.getProfileImageUrl());
        dto.setAadhaarImageUrl(rider.getAadhaarImageUrl());
        dto.setLicenseImageUrl(rider.getLicenseImageUrl());
        enrichWalletAndHistory(dto, rider.getId(), includeRecentOrders);
        return dto;
    }

    /**
     * BLOCKED → ORDER_ASSIGNED (active trip) → ONLINE → OFFLINE.
     */
    private String computeRiderStatus(RiderEntity rider) {
        if (Boolean.TRUE.equals(rider.getIsBlocked())) {
            return "BLOCKED";
        }
        Long id = rider.getId();
        if (id != null) {
            List<OrderStatus> activeAssignment = List.of(
                    OrderStatus.RIDER_ACCEPTED,
                    OrderStatus.PAYMENT_PENDING,
                    OrderStatus.CONFIRMED,
                    OrderStatus.PICKED_UP,
                    OrderStatus.IN_TRANSIT);
            if (orderRepository.existsByRiderIdAndStatusIn(id, activeAssignment)) {
                return "ORDER_ASSIGNED";
            }
        }
        if (Boolean.TRUE.equals(rider.getIsAvailable())) {
            return "ONLINE";
        }
        return "OFFLINE";
    }

    private void enrichWalletAndHistory(RiderResponseDTO dto, Long riderId, boolean includeRecentOrders) {
        if (riderId == null) {
            return;
        }
        dto.setTotalOrdersDelivered(orderRepository.countByRiderIdAndStatus(riderId, OrderStatus.DELIVERED));

        RiderWalletEntity w = riderWalletRepository.findByRiderId(riderId).orElse(null);
        if (w != null) {
            dto.setWalletCurrentBalance(round2(w.getCurrentBalance()));
            dto.setWalletTotalEarnings(round2(w.getTotalEarnings()));
            dto.setWalletTotalWithdrawn(round2(w.getTotalWithdrawn()));
            dto.setWalletCodPendingAmount(round2(w.getCodPendingAmount()));
            dto.setWalletWithdrawalPendingAmount(round2(w.getWithdrawalPendingAmount()));
            dto.setWalletNetAvailable(
                    round2(w.getCurrentBalance() - w.getCodPendingAmount() - w.getWithdrawalPendingAmount()));
        }

        var page = PageRequest.of(0, 10);
        dto.setRecentWalletTransactions(
                riderWalletTransactionRepository.findByRiderIdOrderByCreatedAtDesc(riderId, page).stream()
                        .map(this::toWalletTxnDto)
                        .collect(Collectors.toList()));
        dto.setRecentWithdrawals(
                riderWithdrawalRepository.findByRiderIdOrderByCreatedAtDesc(riderId, page).stream()
                        .map(this::toWithdrawalDto)
                        .collect(Collectors.toList()));
        if (includeRecentOrders) {
            dto.setRecentOrders(
                    orderRepository.findByRiderIdOrderByCreatedAtDesc(riderId, page).stream()
                            .map(this::toRiderOrderPreview)
                            .collect(Collectors.toList()));
        }
    }

    private RiderWalletTransactionDTO toWalletTxnDto(RiderWalletTransactionEntity e) {
        RiderWalletTransactionDTO d = new RiderWalletTransactionDTO();
        d.setId(e.getId());
        d.setType(e.getType() != null ? e.getType().name() : null);
        d.setAmount(e.getAmount());
        d.setReferenceType(e.getReferenceType() != null ? e.getReferenceType().name() : null);
        d.setReferenceId(e.getReferenceId());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setNote(e.getNote());
        d.setMetadataJson(e.getMetadataJson());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return d;
    }

    private RiderWithdrawalDTO toWithdrawalDto(RiderWithdrawalEntity e) {
        RiderWithdrawalDTO d = new RiderWithdrawalDTO();
        d.setId(e.getId());
        d.setAmount(e.getAmount());
        d.setStatus(e.getStatus() != null ? e.getStatus().name() : null);
        d.setAccountHolderName(e.getBankAccountName());
        d.setAccountNumber(e.getBankAccountNumber());
        d.setIfsc(e.getBankIfsc());
        d.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        return d;
    }

    private OrderResponseDTO toRiderOrderPreview(OrderEntity o) {
        return OrderResponseDTO.builder()
                .id(o.getId())
                .userId(o.getUserId())
                .paymentType(o.getPaymentType())
                .status(o.getStatus())
                .riderId(o.getRiderId())
                .totalAmount(o.getTotalAmount())
                .displayOrderId(o.getDisplayOrderId())
                .paymentStatus(o.getPaymentStatus())
                .codCollectedAmount(o.getCodCollectedAmount())
                .codCollectionMode(o.getCodCollectionMode())
                .codSettlementStatus(o.getCodSettlementStatus())
                .distanceKm(o.getDistanceKm())
                .createdAt(o.getCreatedAt() != null ? o.getCreatedAt().toString() : null)
                .build();
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private String generateUniquePublicId() {
        // Not a security boundary; primary intent is to avoid exposing sequential
        // numeric ids.
        // Keep it short and URL-safe: rd-<8 base36 chars>.
        for (int i = 0; i < 10; i++) {
            long n = Math.abs(RIDER_ID_RANDOM.nextLong());
            String suffix = Long.toString(n, 36).toLowerCase(Locale.ROOT);
            if (suffix.length() > 8) {
                suffix = suffix.substring(0, 8);
            } else if (suffix.length() < 8) {
                suffix = "0".repeat(8 - suffix.length()) + suffix;
            }
            String id = "rd-" + suffix;
            if (riderRepository.findByPublicId(id).isEmpty()) {
                return id;
            }
        }
        throw new RuntimeException("Failed to generate rider id");
    }
}

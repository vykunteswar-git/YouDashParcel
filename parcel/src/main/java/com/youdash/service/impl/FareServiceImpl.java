package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.FareCalculateRequestDTO;
import com.youdash.dto.FareCalculateResponseDTO;
import com.youdash.entity.VehicleEntity;
import com.youdash.repository.VehicleRepository;
import com.youdash.service.FareService;
import com.youdash.util.GeoDistanceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Service
public class FareServiceImpl implements FareService {

    @Autowired
    private VehicleRepository vehicleRepository;

    @Value("${youdash.outstation.pricePerKm:0}")
    private double outstationPricePerKm;

    @Value("${youdash.fare.platformFee:0}")
    private double platformFee;

    @Value("${youdash.fare.gstPercent:0}")
    private double gstPercent;

    @Override
    public ApiResponse<FareCalculateResponseDTO> calculateFare(FareCalculateRequestDTO dto) {
        ApiResponse<FareCalculateResponseDTO> response = new ApiResponse<>();
        try {
            validate(dto);

            String serviceType = dto.getServiceType().trim().toUpperCase();

            double distanceKmRaw = GeoDistanceUtil.haversineKm(
                    dto.getPickupLat(), dto.getPickupLng(),
                    dto.getDropLat(), dto.getDropLng()
            );

            BigDecimal distanceKm = BigDecimal.valueOf(distanceKmRaw).setScale(3, RoundingMode.HALF_UP);
            BigDecimal pricePerKm;
            Long vehicleId = null;
            BigDecimal baseFare = BigDecimal.ZERO;
            BigDecimal minimumKm = BigDecimal.ZERO;

            if ("LOCAL".equals(serviceType)) {
                VehicleEntity vehicle = vehicleRepository.findById(Objects.requireNonNull(dto.getVehicleId()))
                        .orElseThrow(() -> new RuntimeException("Vehicle not found with id: " + dto.getVehicleId()));

                if (!Boolean.TRUE.equals(vehicle.getIsActive())) {
                    throw new RuntimeException("Vehicle is inactive");
                }
                if (vehicle.getPricePerKm() == null || vehicle.getPricePerKm() <= 0) {
                    throw new RuntimeException("Vehicle price per Km is not configured");
                }
                if (vehicle.getBaseFare() == null || vehicle.getBaseFare() < 0) {
                    throw new RuntimeException("Vehicle base fare is not configured");
                }
                if (vehicle.getMinimumKm() == null || vehicle.getMinimumKm() < 0) {
                    throw new RuntimeException("Vehicle minimum Km is not configured");
                }
                vehicleId = vehicle.getId();
                pricePerKm = BigDecimal.valueOf(vehicle.getPricePerKm());
                baseFare = BigDecimal.valueOf(vehicle.getBaseFare());
                minimumKm = BigDecimal.valueOf(vehicle.getMinimumKm());
            } else {
                // OUTSTATION: rate comes from config (not vehicle selection)
                if (outstationPricePerKm <= 0) {
                    throw new RuntimeException("Outstation price per Km is not configured");
                }
                pricePerKm = BigDecimal.valueOf(outstationPricePerKm);
            }

            BigDecimal subTotal;
            if ("LOCAL".equals(serviceType)) {
                // If distance is below minimumKm, show baseFare only.
                // If distance exceeds minimumKm, charge per-km only for the extra distance.
                BigDecimal chargeableKm = distanceKm.subtract(minimumKm);
                if (chargeableKm.signum() < 0) chargeableKm = BigDecimal.ZERO;
                subTotal = baseFare.add(chargeableKm.multiply(pricePerKm)).setScale(2, RoundingMode.HALF_UP);
            } else {
                subTotal = distanceKm.multiply(pricePerKm).setScale(2, RoundingMode.HALF_UP);
            }

            BigDecimal platformFeeAmount = BigDecimal.valueOf(platformFee).setScale(2, RoundingMode.HALF_UP);
            if (platformFeeAmount.signum() < 0) platformFeeAmount = BigDecimal.ZERO;

            BigDecimal gstPercentBd = BigDecimal.valueOf(gstPercent);
            if (gstPercentBd.signum() < 0) gstPercentBd = BigDecimal.ZERO;

            BigDecimal gstAmount = subTotal
                    .multiply(gstPercentBd)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal total = subTotal.add(platformFeeAmount).add(gstAmount).setScale(2, RoundingMode.HALF_UP);

            FareCalculateResponseDTO out = new FareCalculateResponseDTO();
            out.setServiceType(serviceType);
            out.setVehicleId(vehicleId);
            out.setDistanceKm(distanceKm.doubleValue());
            out.setPricePerKm(pricePerKm.doubleValue());
            out.setSubTotal(subTotal.doubleValue());
            out.setPlatformFee(platformFeeAmount.doubleValue());
            out.setGstPercent(gstPercentBd.doubleValue());
            out.setGstAmount(gstAmount.doubleValue());
            out.setTotalAmount(total.doubleValue());

            response.setData(out);
            response.setMessage("Fare calculated successfully");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    private void validate(FareCalculateRequestDTO dto) {
        if (dto == null) throw new RuntimeException("Request body is required");

        if (dto.getServiceType() == null || dto.getServiceType().trim().isEmpty())
            throw new RuntimeException("serviceType is required (LOCAL or OUTSTATION)");
        if (dto.getPickupLat() == null || dto.getPickupLng() == null) throw new RuntimeException("pickupLat and pickupLng are required");
        if (dto.getDropLat() == null || dto.getDropLng() == null) throw new RuntimeException("dropLat and dropLng are required");

        String serviceType = dto.getServiceType().trim().toUpperCase();
        if (!"LOCAL".equals(serviceType) && !"OUTSTATION".equals(serviceType)) {
            throw new RuntimeException("serviceType must be LOCAL or OUTSTATION");
        }
        if ("LOCAL".equals(serviceType) && dto.getVehicleId() == null) {
            throw new RuntimeException("vehicleId is required for LOCAL serviceType");
        }

        validateLatLng(dto.getPickupLat(), dto.getPickupLng(), "pickup");
        validateLatLng(dto.getDropLat(), dto.getDropLng(), "drop");
    }

    private void validateLatLng(Double lat, Double lng, String prefix) {
        if (lat < -90 || lat > 90) throw new RuntimeException(prefix + "Lat must be between -90 and 90");
        if (lng < -180 || lng > 180) throw new RuntimeException(prefix + "Lng must be between -180 and 180");
    }
}


package com.youdash.service.impl;

import com.youdash.service.DistanceService;
import org.springframework.stereotype.Service;

@Service
public class HaversineDistanceService implements DistanceService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public double calculateDistanceKm(double pickupLat, double pickupLng, double deliveryLat, double deliveryLng) {
        double dLat = Math.toRadians(deliveryLat - pickupLat);
        double dLng = Math.toRadians(deliveryLng - pickupLng);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(pickupLat)) * Math.cos(Math.toRadians(deliveryLat))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}


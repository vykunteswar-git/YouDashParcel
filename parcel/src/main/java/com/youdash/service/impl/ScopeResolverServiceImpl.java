package com.youdash.service.impl;

import com.youdash.pricing.DeliveryScope;
import com.youdash.repository.GlobalDeliveryConfigRepository;
import com.youdash.service.ScopeResolverService;
import com.youdash.service.ZoneGeoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScopeResolverServiceImpl implements ScopeResolverService {

    private static final double DISTANCE_ONLY_OUTSTATION_THRESHOLD_KM = 60.0;

    @Autowired
    private GlobalDeliveryConfigRepository globalDeliveryConfigRepository;

    @Autowired
    private ZoneGeoService zoneGeoService;

    @Override
    public DeliveryScope resolveScope(double distanceKm) {
        if (distanceKm < 0) {
            return DeliveryScope.OUT_CITY;
        }
        double threshold = globalDeliveryConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .map(g -> g.getIncityExtensionKm() == null ? null : g.getIncityExtensionKm() + 10.0)
                .orElse(DISTANCE_ONLY_OUTSTATION_THRESHOLD_KM);
        if (threshold <= 0) {
            threshold = DISTANCE_ONLY_OUTSTATION_THRESHOLD_KM;
        }
        return distanceKm <= threshold ? DeliveryScope.IN_CITY : DeliveryScope.OUT_CITY;
    }

    @Override
    public DeliveryScope resolveScopeFromGeo(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        return zoneGeoService.isSameIncityZone(pickupLat, pickupLng, dropLat, dropLng)
                ? DeliveryScope.IN_CITY
                : DeliveryScope.OUT_CITY;
    }
}

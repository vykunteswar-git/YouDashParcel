package com.youdash.service;

import com.youdash.pricing.DeliveryScope;

public interface ScopeResolverService {

    DeliveryScope resolveScope(double distanceKm);

    /**
     * INCITY only when pickup and drop lie in the same active zone (circle).
     */
    DeliveryScope resolveScopeFromGeo(double pickupLat, double pickupLng, double dropLat, double dropLng);
}


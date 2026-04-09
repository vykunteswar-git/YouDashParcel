package com.youdash.service;

import com.youdash.pricing.DeliveryScope;

public interface ScopeResolverService {
    DeliveryScope resolveScope(double distanceKm);
}


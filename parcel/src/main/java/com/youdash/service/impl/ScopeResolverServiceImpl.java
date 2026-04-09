package com.youdash.service.impl;

import com.youdash.pricing.DeliveryScope;
import com.youdash.service.ScopeResolverService;
import org.springframework.stereotype.Service;

@Service
public class ScopeResolverServiceImpl implements ScopeResolverService {

    @Override
    public DeliveryScope resolveScope(double distanceKm) {
        return distanceKm <= 15.0 ? DeliveryScope.IN_CITY : DeliveryScope.OUT_CITY;
    }
}


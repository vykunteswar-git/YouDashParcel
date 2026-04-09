package com.youdash.service.impl;

import com.youdash.pricing.DeliveryScope;
import com.youdash.repository.InCityRadiusConfigRepository;
import com.youdash.service.ScopeResolverService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ScopeResolverServiceImpl implements ScopeResolverService {

    @Autowired
    private InCityRadiusConfigRepository inCityRadiusConfigRepository;

    @Override
    public DeliveryScope resolveScope(double distanceKm) {
        double radiusKm = inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .map(cfg -> cfg.getRadiusKm() == null ? null : cfg.getRadiusKm().doubleValue())
                .filter(r -> r != null && r > 0)
                .orElse(60.0);

        return distanceKm <= radiusKm ? DeliveryScope.IN_CITY : DeliveryScope.OUT_CITY;
    }
}


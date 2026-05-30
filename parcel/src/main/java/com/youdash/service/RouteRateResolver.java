package com.youdash.service;

import com.youdash.entity.AppConfigEntity;
import com.youdash.entity.HubEntity;
import com.youdash.entity.HubRouteEntity;
import com.youdash.entity.ZoneRouteEntity;
import com.youdash.repository.HubRepository;
import com.youdash.repository.HubRouteRepository;
import com.youdash.repository.ZoneRouteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Outstation hub-leg rate: hub pair override → zone pair → app default.
 */
@Component
public class RouteRateResolver {

    @Autowired
    private HubRouteRepository hubRouteRepository;

    @Autowired
    private ZoneRouteRepository zoneRouteRepository;

    @Autowired
    private HubRepository hubRepository;

    public double resolveHubLegRatePerKm(Long originHubId, Long destinationHubId, AppConfigEntity config) {
        Optional<HubRouteEntity> hubRoute = hubRouteRepository
                .findByOriginHubIdAndDestinationHubIdAndIsActiveTrue(originHubId, destinationHubId);
        if (hubRoute.isPresent()) {
            return hubRoute.get().getRatePerKm();
        }
        Optional<ZoneRouteEntity> zoneRoute = resolveZoneRoute(originHubId, destinationHubId);
        if (zoneRoute.isPresent()) {
            return zoneRoute.get().getRatePerKm();
        }
        return nz(config.getDefaultRouteRatePerKm());
    }

    public Optional<Long> resolveZoneRouteId(Long originHubId, Long destinationHubId) {
        return resolveZoneRoute(originHubId, destinationHubId).map(ZoneRouteEntity::getId);
    }

    public Optional<ZoneRouteEntity> resolveZoneRoute(Long originHubId, Long destinationHubId) {
        if (originHubId == null || destinationHubId == null) {
            return Optional.empty();
        }
        HubEntity origin = hubRepository.findById(originHubId).orElse(null);
        HubEntity dest = hubRepository.findById(destinationHubId).orElse(null);
        if (origin == null || dest == null || origin.getZoneId() == null || dest.getZoneId() == null) {
            return Optional.empty();
        }
        if (origin.getZoneId().equals(dest.getZoneId())) {
            return Optional.empty();
        }
        return zoneRouteRepository.findByOriginZoneIdAndDestinationZoneIdAndIsActiveTrue(
                origin.getZoneId(), dest.getZoneId());
    }

    public Optional<Long> resolveHubRouteId(Long originHubId, Long destinationHubId) {
        return hubRouteRepository
                .findByOriginHubIdAndDestinationHubIdAndIsActiveTrue(originHubId, destinationHubId)
                .map(HubRouteEntity::getId);
    }

    private static double nz(Double v) {
        return v == null ? 0.0 : v;
    }
}

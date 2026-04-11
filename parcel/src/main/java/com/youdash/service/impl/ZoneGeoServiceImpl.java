package com.youdash.service.impl;

import com.youdash.entity.GlobalDeliveryConfigEntity;
import com.youdash.entity.ZoneEntity;
import com.youdash.repository.GlobalDeliveryConfigRepository;
import com.youdash.repository.ZoneRepository;
import com.youdash.service.ZoneGeoService;
import com.youdash.util.GeoDistanceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ZoneGeoServiceImpl implements ZoneGeoService {

    @Autowired
    private ZoneRepository zoneRepository;

    @Autowired
    private GlobalDeliveryConfigRepository globalDeliveryConfigRepository;

    /**
     * Points within {@code zone.radiusKm + incityExtensionKm} (from active global config) count as inside the zone
     * for incity detection (service availability, pricing, orders).
     */
    @Override
    public Optional<ZoneEntity> findZoneContaining(double lat, double lng) {
        double extensionKm = resolveIncityExtensionKm();
        List<ZoneEntity> zones = zoneRepository.findByIsActiveTrue();
        return zones.stream()
                .filter(z -> z.getCenterLat() != null && z.getCenterLng() != null && z.getRadiusKm() != null)
                .filter(z -> {
                    double d = GeoDistanceUtil.haversineKm(lat, lng, z.getCenterLat(), z.getCenterLng());
                    double effectiveRadiusKm = z.getRadiusKm() + extensionKm;
                    return d <= effectiveRadiusKm;
                })
                .min(Comparator.comparing(ZoneEntity::getRadiusKm));
    }

    private double resolveIncityExtensionKm() {
        return globalDeliveryConfigRepository.findFirstByActiveTrueOrderByIdDesc()
                .map(GlobalDeliveryConfigEntity::getIncityExtensionKm)
                .filter(Objects::nonNull)
                .filter(k -> k >= 0)
                .orElse(0.0);
    }

    @Override
    public boolean isSameIncityZone(double pickupLat, double pickupLng, double dropLat, double dropLng) {
        Optional<ZoneEntity> p = findZoneContaining(pickupLat, pickupLng);
        Optional<ZoneEntity> d = findZoneContaining(dropLat, dropLng);
        return p.isPresent() && d.isPresent() && p.get().getId().equals(d.get().getId());
    }
}

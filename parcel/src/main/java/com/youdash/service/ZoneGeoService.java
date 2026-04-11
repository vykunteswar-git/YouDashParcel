package com.youdash.service;

import com.youdash.entity.ZoneEntity;

import java.util.Optional;

public interface ZoneGeoService {

    Optional<ZoneEntity> findZoneContaining(double lat, double lng);

    boolean isSameIncityZone(double pickupLat, double pickupLng, double dropLat, double dropLng);
}

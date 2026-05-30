package com.youdash.repository;

import com.youdash.entity.ZoneRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ZoneRouteRepository extends JpaRepository<ZoneRouteEntity, Long> {

    Optional<ZoneRouteEntity> findByOriginZoneIdAndDestinationZoneIdAndIsActiveTrue(
            Long originZoneId, Long destinationZoneId);

    Optional<ZoneRouteEntity> findByOriginZoneIdAndDestinationZoneId(
            Long originZoneId, Long destinationZoneId);
}

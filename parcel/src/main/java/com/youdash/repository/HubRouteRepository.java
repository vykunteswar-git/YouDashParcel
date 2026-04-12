package com.youdash.repository;

import com.youdash.entity.HubRouteEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HubRouteRepository extends JpaRepository<HubRouteEntity, Long> {

    Optional<HubRouteEntity> findByOriginHubIdAndDestinationHubIdAndIsActiveTrue(Long originHubId, Long destinationHubId);
}

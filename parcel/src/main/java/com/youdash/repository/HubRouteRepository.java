package com.youdash.repository;

import com.youdash.entity.HubRouteEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HubRouteRepository extends JpaRepository<HubRouteEntity, Long> {

    @EntityGraph(attributePaths = {"sourceHub", "destinationHub"})
    Optional<HubRouteEntity> findById(Long id);

    @EntityGraph(attributePaths = {"sourceHub", "destinationHub"})
    Optional<HubRouteEntity> findBySourceHub_IdAndDestinationHub_IdAndIsActiveTrue(Long sourceHubId, Long destinationHubId);

    @EntityGraph(attributePaths = {"sourceHub", "destinationHub"})
    List<HubRouteEntity> findByIsActiveTrue();

    @EntityGraph(attributePaths = {"sourceHub", "destinationHub"})
    @Override
    List<HubRouteEntity> findAll(Sort sort);
}

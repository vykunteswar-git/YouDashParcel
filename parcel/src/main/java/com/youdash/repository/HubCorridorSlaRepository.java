package com.youdash.repository;

import com.youdash.entity.HubCorridorSlaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HubCorridorSlaRepository extends JpaRepository<HubCorridorSlaEntity, Long> {

    List<HubCorridorSlaEntity> findByHubIdAndDestinationZoneIdAndIsActiveTrueOrderByPriorityAsc(
            Long hubId, Long destinationZoneId);

    List<HubCorridorSlaEntity> findByHubIdOrderByDestinationZoneIdAscPriorityAsc(Long hubId);
}

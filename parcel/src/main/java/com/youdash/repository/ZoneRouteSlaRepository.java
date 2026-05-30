package com.youdash.repository;

import com.youdash.entity.ZoneRouteSlaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRouteSlaRepository extends JpaRepository<ZoneRouteSlaEntity, Long> {

    List<ZoneRouteSlaEntity> findByZoneRouteIdAndIsActiveTrueOrderByPriorityAsc(Long zoneRouteId);

    List<ZoneRouteSlaEntity> findByZoneRouteIdOrderByPriorityAsc(Long zoneRouteId);
}

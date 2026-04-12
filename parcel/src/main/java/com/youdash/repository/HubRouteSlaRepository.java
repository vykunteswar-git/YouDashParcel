package com.youdash.repository;

import com.youdash.entity.HubRouteSlaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HubRouteSlaRepository extends JpaRepository<HubRouteSlaEntity, Long> {

    List<HubRouteSlaEntity> findByHubRouteIdAndIsActiveTrueOrderByPriorityAsc(Long hubRouteId);

    List<HubRouteSlaEntity> findByHubRouteIdOrderByPriorityAsc(Long hubRouteId);
}

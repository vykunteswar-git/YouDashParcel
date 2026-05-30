package com.youdash.repository;

import com.youdash.entity.OutstationLegRateTierEntity;
import com.youdash.model.OutstationLegType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutstationLegRateTierRepository extends JpaRepository<OutstationLegRateTierEntity, Long> {

    List<OutstationLegRateTierEntity> findByLegTypeAndIsActiveTrueOrderBySortOrderAscMinWeightKgAsc(
            OutstationLegType legType);

    List<OutstationLegRateTierEntity> findByLegTypeOrderBySortOrderAscMinWeightKgAsc(OutstationLegType legType);

    void deleteByLegType(OutstationLegType legType);
}

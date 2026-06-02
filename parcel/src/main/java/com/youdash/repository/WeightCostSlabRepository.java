package com.youdash.repository;

import com.youdash.entity.WeightCostSlabEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WeightCostSlabRepository extends JpaRepository<WeightCostSlabEntity, Long> {

    List<WeightCostSlabEntity> findByIsActiveTrueOrderBySortOrderAscMinWeightKgAsc();

    List<WeightCostSlabEntity> findAllByOrderBySortOrderAscMinWeightKgAsc();
}

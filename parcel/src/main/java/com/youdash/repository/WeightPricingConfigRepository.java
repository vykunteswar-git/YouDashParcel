package com.youdash.repository;

import com.youdash.entity.WeightPricingConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeightPricingConfigRepository extends JpaRepository<WeightPricingConfigEntity, Long> {

    Optional<WeightPricingConfigEntity> findFirstByActiveTrueOrderByIdDesc();
}

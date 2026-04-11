package com.youdash.repository;

import com.youdash.entity.ZonePricingEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ZonePricingRepository extends JpaRepository<ZonePricingEntity, Long> {

    @EntityGraph(attributePaths = "zone")
    Optional<ZonePricingEntity> findById(Long id);

    @EntityGraph(attributePaths = "zone")
    Optional<ZonePricingEntity> findByZone_IdAndIsActiveTrue(Long zoneId);

    @EntityGraph(attributePaths = "zone")
    @Override
    List<ZonePricingEntity> findAll(Sort sort);

    void deleteByZone_Id(Long zoneId);
}

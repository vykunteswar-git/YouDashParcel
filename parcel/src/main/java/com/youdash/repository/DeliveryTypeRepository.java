package com.youdash.repository;

import com.youdash.entity.DeliveryTypeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeliveryTypeRepository extends JpaRepository<DeliveryTypeEntity, Long> {
    Optional<DeliveryTypeEntity> findByNameIgnoreCaseAndActiveTrue(String name);
    List<DeliveryTypeEntity> findByActiveTrueOrderByNameAsc();
}


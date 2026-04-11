package com.youdash.repository;

import com.youdash.entity.GlobalDeliveryConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GlobalDeliveryConfigRepository extends JpaRepository<GlobalDeliveryConfigEntity, Long> {

    Optional<GlobalDeliveryConfigEntity> findFirstByActiveTrueOrderByIdDesc();
}

package com.youdash.repository;

import com.youdash.entity.PlatformFeeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlatformFeeRepository extends JpaRepository<PlatformFeeEntity, Long> {
    Optional<PlatformFeeEntity> findFirstByActiveTrueOrderByIdDesc();
}


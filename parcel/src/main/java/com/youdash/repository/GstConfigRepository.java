package com.youdash.repository;

import com.youdash.entity.GstConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GstConfigRepository extends JpaRepository<GstConfigEntity, Long> {
    Optional<GstConfigEntity> findFirstByActiveTrueOrderByIdDesc();
}


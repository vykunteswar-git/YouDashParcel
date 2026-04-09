package com.youdash.repository;

import com.youdash.entity.InCityRadiusConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InCityRadiusConfigRepository extends JpaRepository<InCityRadiusConfigEntity, Long> {
    Optional<InCityRadiusConfigEntity> findFirstByActiveTrueOrderByIdDesc();
}


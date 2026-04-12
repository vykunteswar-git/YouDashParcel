package com.youdash.repository;

import com.youdash.entity.ZoneEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ZoneRepository extends JpaRepository<ZoneEntity, Long> {

    List<ZoneEntity> findByIsActiveTrueOrderByIdAsc();
}

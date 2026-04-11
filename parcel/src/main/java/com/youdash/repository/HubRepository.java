package com.youdash.repository;

import com.youdash.entity.HubEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HubRepository extends JpaRepository<HubEntity, Long> {

    List<HubEntity> findByCityIgnoreCase(String city, Sort sort);

    List<HubEntity> findByIsActiveTrue();
}

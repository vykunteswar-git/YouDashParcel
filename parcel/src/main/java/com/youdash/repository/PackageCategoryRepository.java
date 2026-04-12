package com.youdash.repository;

import com.youdash.entity.PackageCategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PackageCategoryRepository extends JpaRepository<PackageCategoryEntity, Long> {

    List<PackageCategoryEntity> findByIsActiveTrueOrderBySortOrderAscIdAsc();
}

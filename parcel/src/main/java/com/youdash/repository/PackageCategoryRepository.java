package com.youdash.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.PackageCategoryEntity;

@Repository
public interface PackageCategoryRepository extends JpaRepository<PackageCategoryEntity, Long> {
}

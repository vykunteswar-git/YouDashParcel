package com.youdash.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.youdash.entity.PackageItemEntity;
import java.util.List;

@Repository
public interface PackageItemRepository extends JpaRepository<PackageItemEntity, Long> {
    List<PackageItemEntity> findByPackageCategoryIdAndIsActiveTrue(Long packageCategoryId);
}

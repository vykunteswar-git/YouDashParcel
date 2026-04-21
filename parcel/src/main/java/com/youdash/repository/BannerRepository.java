package com.youdash.repository;

import com.youdash.entity.BannerEntity;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BannerRepository extends JpaRepository<BannerEntity, Long> {
    List<BannerEntity> findByIsActiveTrue(Sort sort);
}

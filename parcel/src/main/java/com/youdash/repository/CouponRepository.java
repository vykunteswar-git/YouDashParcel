package com.youdash.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.CouponEntity;

@Repository
public interface CouponRepository extends JpaRepository<CouponEntity, Long> {

    Optional<CouponEntity> findByCodeIgnoreCase(String code);

    List<CouponEntity> findByActiveTrueOrderByValidToAsc();
}

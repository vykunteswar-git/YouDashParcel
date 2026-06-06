package com.youdash.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.CouponRedemptionEntity;

@Repository
public interface CouponRedemptionRepository extends JpaRepository<CouponRedemptionEntity, Long> {

    long countByCouponIdAndUserId(Long couponId, Long userId);

    boolean existsByOrderId(Long orderId);

    Optional<CouponRedemptionEntity> findByOrderId(Long orderId);

    void deleteByOrderId(Long orderId);
}

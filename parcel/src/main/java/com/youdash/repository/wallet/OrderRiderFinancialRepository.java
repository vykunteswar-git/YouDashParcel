package com.youdash.repository.wallet;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.OrderRiderFinancialEntity;

@Repository
public interface OrderRiderFinancialRepository extends JpaRepository<OrderRiderFinancialEntity, Long> {

    Optional<OrderRiderFinancialEntity> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<OrderRiderFinancialEntity> findByOrderIdIn(Collection<Long> orderIds);

    @Query("""
            SELECT COALESCE(SUM(f.riderEarningAmount), 0.0)
            FROM OrderRiderFinancialEntity f
            WHERE f.riderId = :riderId
              AND f.createdAt >= :fromTs
              AND f.createdAt < :toTs
            """)
    Double sumRiderEarningsBetween(
            @Param("riderId") Long riderId,
            @Param("fromTs") Instant fromTs,
            @Param("toTs") Instant toTs);
}

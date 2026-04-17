package com.youdash.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.youdash.entity.OrderDispatchEntity;

public interface OrderDispatchRepository extends JpaRepository<OrderDispatchEntity, Long> {

    boolean existsByOrderIdAndRiderId(Long orderId, Long riderId);

    List<OrderDispatchEntity> findByOrderId(Long orderId);

    @Modifying
    @Query("""
            update OrderDispatchEntity d
               set d.status = :status
             where d.orderId = :orderId
               and d.riderId = :riderId
            """)
    int updateStatus(@Param("orderId") Long orderId, @Param("riderId") Long riderId, @Param("status") String status);

    @Modifying
    @Query("""
            update OrderDispatchEntity d
               set d.status = :status
             where d.orderId = :orderId
            """)
    int updateAllStatus(@Param("orderId") Long orderId, @Param("status") String status);
}


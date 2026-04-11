package com.youdash.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.youdash.entity.OrderEntity;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByUserId(Long userId);

    Optional<OrderEntity> findByRazorpayOrderId(String razorpayOrderId);

    Optional<OrderEntity> findByOrderId(String orderId);

    List<OrderEntity> findByStatusOrderByCreatedAtDesc(String status);

    List<OrderEntity> findByRiderIdOrderByUpdatedAtDesc(Long riderId);

    List<OrderEntity> findByDeliveryRiderIdOrderByUpdatedAtDesc(Long deliveryRiderId);

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM OrderEntity o WHERE o.riderId = :rid AND o.status IN :statuses")
    boolean existsByRiderIdAndStatusIn(@Param("rid") Long rid, @Param("statuses") Collection<String> statuses);

    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM OrderEntity o WHERE o.deliveryRiderId = :rid AND o.status IN :statuses")
    boolean existsByDeliveryRiderIdAndStatusIn(@Param("rid") Long rid, @Param("statuses") Collection<String> statuses);
}

package com.youdash.repository;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByRiderIdAndStatus(Long riderId, OrderStatus status);

    List<OrderEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    List<OrderEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, Pageable pageable);

    List<OrderEntity> findByRiderIdAndServiceModeAndStatusIn(Long riderId, ServiceMode serviceMode, List<OrderStatus> statuses);

    Optional<OrderEntity> findByDisplayOrderId(String displayOrderId);

    Optional<OrderEntity> findByRazorpayOrderId(String razorpayOrderId);
}

package com.youdash.repository;

import com.youdash.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<OrderEntity> findByDisplayOrderId(String displayOrderId);

    Optional<OrderEntity> findByRazorpayOrderId(String razorpayOrderId);
}

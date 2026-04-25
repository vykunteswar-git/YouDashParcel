package com.youdash.repository;

import com.youdash.entity.OrderTimelineEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderTimelineEventRepository extends JpaRepository<OrderTimelineEventEntity, Long> {
    List<OrderTimelineEventEntity> findByOrderIdOrderByCreatedAtAsc(Long orderId);
}

package com.youdash.service;

import com.youdash.dto.OrderTimelineEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;

import java.util.List;

public interface OrderTimelineService {
    void appendEvent(
            OrderEntity order,
            OrderStatus status,
            String eventType,
            Long hubId,
            Long riderId,
            String location,
            String notes);

    List<OrderTimelineEventDTO> timelineForOrder(Long orderId);
}

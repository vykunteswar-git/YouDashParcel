package com.youdash.service.impl;

import com.youdash.dto.OrderTimelineEventDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.OrderTimelineEventEntity;
import com.youdash.model.OrderStatus;
import com.youdash.repository.OrderTimelineEventRepository;
import com.youdash.service.OrderTimelineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderTimelineServiceImpl implements OrderTimelineService {

    @Autowired
    private OrderTimelineEventRepository orderTimelineEventRepository;

    @Override
    public void appendEvent(
            OrderEntity order,
            OrderStatus status,
            String eventType,
            Long hubId,
            Long riderId,
            String location,
            String notes) {
        if (order == null || order.getId() == null || status == null) {
            return;
        }
        OrderTimelineEventEntity evt = new OrderTimelineEventEntity();
        evt.setOrderId(order.getId());
        evt.setStatus(status);
        evt.setEventType(eventType);
        evt.setHubId(hubId);
        evt.setRiderId(riderId);
        evt.setLocation(location);
        evt.setNotes(notes);
        evt.setEventVersion(1);
        orderTimelineEventRepository.save(evt);
    }

    @Override
    public List<OrderTimelineEventDTO> timelineForOrder(Long orderId) {
        return orderTimelineEventRepository.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .map(e -> OrderTimelineEventDTO.builder()
                        .status(e.getStatus())
                        .eventType(e.getEventType())
                        .eventVersion(e.getEventVersion())
                        .timestamp(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null)
                        .location(e.getLocation())
                        .hubId(e.getHubId())
                        .riderId(e.getRiderId())
                        .notes(e.getNotes())
                        .build())
                .toList();
    }
}

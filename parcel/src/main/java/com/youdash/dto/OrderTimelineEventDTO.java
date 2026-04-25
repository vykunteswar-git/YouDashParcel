package com.youdash.dto;

import com.youdash.model.OrderStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OrderTimelineEventDTO {
    private OrderStatus status;
    private String eventType;
    private Integer eventVersion;
    private String timestamp;
    private String location;
    private Long hubId;
    private Long riderId;
    private String notes;
}

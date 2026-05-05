package com.youdash.service;

import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;

import java.util.Set;

public interface OrderStatusTransitionGuard {
    void ensureAllowed(ServiceMode serviceMode, OrderStatus from, OrderStatus to);

    Set<OrderStatus> allowedNextStatuses(ServiceMode serviceMode, OrderStatus current);

    Set<OrderStatus> adminSelectableNextStatuses(ServiceMode serviceMode, OrderStatus current);
}

package com.youdash.service;

import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;

public interface OrderStatusTransitionGuard {
    void ensureAllowed(ServiceMode serviceMode, OrderStatus from, OrderStatus to);
}

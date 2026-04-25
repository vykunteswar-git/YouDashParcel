package com.youdash.service.impl;

import com.youdash.exception.BadRequestException;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.service.OrderStatusTransitionGuard;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Service
public class OrderStatusTransitionGuardImpl implements OrderStatusTransitionGuard {

    private static final Map<OrderStatus, Set<OrderStatus>> INCITY_ALLOWED = Map.ofEntries(
            Map.entry(OrderStatus.SEARCHING_RIDER, Set.of(OrderStatus.RIDER_ACCEPTED, OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.RIDER_ACCEPTED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.CONFIRMED, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> OUTSTATION_ALLOWED = Map.ofEntries(
            Map.entry(OrderStatus.CREATED, Set.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.CONFIRMED, Set.of(OrderStatus.PICKED_UP, OrderStatus.AT_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.DEPARTED_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.DEPARTED_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.READY_FOR_PICKUP, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.SORTED_AT_DESTINATION, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.SORTED_AT_DESTINATION, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.READY_FOR_PICKUP, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.READY_FOR_PICKUP, Set.of(OrderStatus.DELIVERED, OrderStatus.RETURNED)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    @Override
    public void ensureAllowed(ServiceMode serviceMode, OrderStatus from, OrderStatus to) {
        if (to == null || from == to || from == null || serviceMode == null) {
            return;
        }
        final Map<OrderStatus, Set<OrderStatus>> matrix =
                serviceMode == ServiceMode.OUTSTATION ? OUTSTATION_ALLOWED : INCITY_ALLOWED;
        final Set<OrderStatus> allowed = matrix.get(from);
        if (allowed != null && allowed.contains(to)) {
            return;
        }
        throw new BadRequestException("Invalid transition: " + from + " -> " + to + " for " + serviceMode);
    }
}

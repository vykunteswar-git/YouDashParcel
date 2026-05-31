package com.youdash.service.impl;

import com.youdash.exception.BadRequestException;
import com.youdash.model.OrderStatus;
import com.youdash.model.ServiceMode;
import com.youdash.service.OrderStatusTransitionGuard;
import com.youdash.util.OutstationCodPolicy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Service
public class OrderStatusTransitionGuardImpl implements OrderStatusTransitionGuard {

    private static final Map<OrderStatus, Set<OrderStatus>> INCITY_ALLOWED = Map.ofEntries(
            Map.entry(OrderStatus.SEARCHING_RIDER, Set.of(OrderStatus.RIDER_ACCEPTED, OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.RIDER_ACCEPTED, Set.of(OrderStatus.PAYMENT_PENDING, OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED, OrderStatus.EXPIRED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.SEARCHING_RIDER, OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.RIDER_ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> D2D_ALLOWED = Map.ofEntries(
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.PICKUP_ASSIGNED, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.PICKUP_ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.FAILED_DELIVERY, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> D2H_ALLOWED = Map.ofEntries(
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.PICKUP_ASSIGNED, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.PICKUP_ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.FAILED_DELIVERY, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.AWAITING_HUB_COLLECTION, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AWAITING_HUB_COLLECTION, Set.of(OrderStatus.COLLECTED, OrderStatus.RETURNED)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> H2D_ALLOWED = Map.ofEntries(
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.CANCELLED, OrderStatus.FAILED)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> D2D_ADMIN = Map.ofEntries(
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.PICKUP_ASSIGNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKUP_ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> D2H_ADMIN = Map.ofEntries(
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.PICKUP_ASSIGNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKUP_ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.AWAITING_HUB_COLLECTION, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AWAITING_HUB_COLLECTION, Set.of(OrderStatus.COLLECTED, OrderStatus.RETURNED)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> H2D_ADMIN = Map.ofEntries(
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.AT_ORIGIN_HUB, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.AT_ORIGIN_HUB, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.AT_DESTINATION_HUB, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.AT_DESTINATION_HUB, Set.of(OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    private static final Map<OrderStatus, Set<OrderStatus>> INCITY_ADMIN = Map.ofEntries(
            Map.entry(OrderStatus.SEARCHING_RIDER, Set.of(OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.RIDER_ACCEPTED, Set.of(OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PAYMENT_PENDING, Set.of(OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.BOOKED, Set.of(OrderStatus.RIDER_ASSIGNED, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.RIDER_ASSIGNED, Set.of(OrderStatus.PICKED_UP, OrderStatus.CANCELLED)),
            Map.entry(OrderStatus.PICKED_UP, Set.of(OrderStatus.IN_TRANSIT, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.IN_TRANSIT, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.OUT_FOR_DELIVERY, Set.of(OrderStatus.DELIVERED, OrderStatus.FAILED_DELIVERY)),
            Map.entry(OrderStatus.FAILED_DELIVERY, Set.of(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.RETURNED)));

    @Override
    public void ensureAllowed(ServiceMode serviceMode, OrderStatus from, OrderStatus to) {
        ensureAllowed(serviceMode, null, from, to);
    }

    @Override
    public void ensureAllowed(ServiceMode serviceMode, String deliveryType, OrderStatus from, OrderStatus to) {
        if (to == null || from == to || from == null || serviceMode == null) {
            return;
        }
        from = OrderStatus.fromLegacy(from.name());
        to = OrderStatus.fromLegacy(to.name());
        if (serviceMode == ServiceMode.OUTSTATION) {
            from = OrderStatus.normalizeOutstationPickupStatus(from);
            to = OrderStatus.normalizeOutstationPickupStatus(to);
        }
        Map<OrderStatus, Set<OrderStatus>> matrix = resolveMatrix(serviceMode, deliveryType, false);
        Set<OrderStatus> allowed = matrix.get(from);
        if (allowed != null && allowed.contains(to)) {
            return;
        }
        throw new BadRequestException("Invalid transition: " + from + " -> " + to + " for " + serviceMode
                + (deliveryType != null ? " (" + deliveryType + ")" : ""));
    }

    @Override
    public Set<OrderStatus> allowedNextStatuses(ServiceMode serviceMode, OrderStatus current) {
        return allowedNextStatuses(serviceMode, null, current);
    }

    @Override
    public Set<OrderStatus> allowedNextStatuses(ServiceMode serviceMode, String deliveryType, OrderStatus current) {
        if (serviceMode == null || current == null) {
            return Set.of();
        }
        current = OrderStatus.fromLegacy(current.name());
        if (serviceMode == ServiceMode.OUTSTATION) {
            current = OrderStatus.normalizeOutstationPickupStatus(current);
        }
        Set<OrderStatus> allowed = resolveMatrix(serviceMode, deliveryType, false).get(current);
        if (allowed == null || allowed.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(allowed);
    }

    @Override
    public Set<OrderStatus> adminSelectableNextStatuses(ServiceMode serviceMode, OrderStatus current) {
        return adminSelectableNextStatuses(serviceMode, null, current);
    }

    @Override
    public Set<OrderStatus> adminSelectableNextStatuses(
            ServiceMode serviceMode, String deliveryType, OrderStatus current) {
        if (serviceMode == null || current == null) {
            return Set.of();
        }
        current = OrderStatus.fromLegacy(current.name());
        if (serviceMode == ServiceMode.OUTSTATION) {
            current = OrderStatus.normalizeOutstationPickupStatus(current);
        }
        Set<OrderStatus> curated = resolveMatrix(serviceMode, deliveryType, true).get(current);
        if (curated == null || curated.isEmpty()) {
            return Set.of();
        }
        return new LinkedHashSet<>(curated);
    }

    private static Map<OrderStatus, Set<OrderStatus>> resolveMatrix(
            ServiceMode serviceMode, String deliveryType, boolean admin) {
        if (serviceMode != ServiceMode.OUTSTATION) {
            return admin ? INCITY_ADMIN : INCITY_ALLOWED;
        }
        String type = deliveryType == null ? "" : deliveryType.trim().toUpperCase();
        if (OutstationCodPolicy.isHubToDoor(type)) {
            return admin ? H2D_ADMIN : H2D_ALLOWED;
        }
        if (OutstationCodPolicy.isDoorToHub(type)) {
            return admin ? D2H_ADMIN : D2H_ALLOWED;
        }
        return admin ? D2D_ADMIN : D2D_ALLOWED;
    }
}

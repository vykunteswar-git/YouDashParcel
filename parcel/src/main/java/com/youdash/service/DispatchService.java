package com.youdash.service;

import com.youdash.entity.OrderEntity;

/**
 * INCITY-only dispatch: notify nearby riders and manage request lifecycle.
 */
public interface DispatchService {

    /** Called after INCITY order is created with status SEARCHING_RIDER. */
    void dispatchNewIncityOrder(OrderEntity order);

    /** Close request for all riders who were notified for the order. */
    void closeRequest(Long orderId, String reason, Long acceptedRiderId);

    /** True if this rider was in the dispatch candidate list (notified) for this order. */
    boolean wasRiderDispatched(Long orderId, Long riderId);

    /** Mark rider as rejected for dispatch optimization. */
    void markRejected(Long orderId, Long riderId);
}


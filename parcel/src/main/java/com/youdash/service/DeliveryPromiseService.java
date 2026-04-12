package com.youdash.service;

import com.youdash.dto.DeliveryPromiseDTO;

public interface DeliveryPromiseService {

    /**
     * @param hubRouteId hub route row id, or null if no configured route
     * @param deliveryTypeUI e.g. DOOR_TO_DOOR / DOOR_TO_HUB / HUB_TO_DOOR
     */
    DeliveryPromiseDTO getDeliveryPromise(Long hubRouteId, String deliveryTypeUI);
}

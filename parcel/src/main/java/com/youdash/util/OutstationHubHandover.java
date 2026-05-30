package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;

public final class OutstationHubHandover {

    public enum Type {
        DROP,
        COLLECT;

        public static Type parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("type is required (DROP or COLLECT)");
            }
            return Type.valueOf(raw.trim().toUpperCase());
        }
    }

    private OutstationHubHandover() {}

    public static boolean canDropAtOriginHub(OrderEntity o) {
        return OutstationCodPolicy.isHubToDoor(o) && o.getStatus() == OrderStatus.BOOKED;
    }

    public static boolean canCollectAtDestinationHub(OrderEntity o) {
        return OutstationCodPolicy.isDoorToHub(o) && o.getStatus() == OrderStatus.AWAITING_HUB_COLLECTION;
    }
}

-- Canonical status rename (run once on existing DB).
-- Safe to re-run: only updates rows that still use legacy values.

UPDATE youdash_orders SET status = 'BOOKED' WHERE status IN ('CREATED', 'ORDER_CREATED');
UPDATE youdash_orders SET status = 'RIDER_ASSIGNED' WHERE status IN ('CONFIRMED', 'PICKUP_CONFIRMED');
UPDATE youdash_orders SET status = 'PICKED_UP' WHERE status = 'PARCEL_PICKED_UP';
UPDATE youdash_orders SET status = 'AT_ORIGIN_HUB' WHERE status = 'ARRIVED_ORIGIN_HUB';
UPDATE youdash_orders SET status = 'IN_TRANSIT' WHERE status IN ('DISPATCHED_TO_DESTINATION', 'DEPARTED_ORIGIN_HUB');
UPDATE youdash_orders SET status = 'AT_DESTINATION_HUB' WHERE status IN ('ARRIVED_DESTINATION_HUB', 'SORTED_AT_DESTINATION');
UPDATE youdash_orders SET status = 'OUT_FOR_DELIVERY' WHERE status = 'DELIVERY_RIDER_ASSIGNED';
UPDATE youdash_orders SET status = 'AWAITING_HUB_COLLECTION' WHERE status = 'READY_FOR_PICKUP';
UPDATE youdash_orders SET status = 'COLLECTED' WHERE status = 'COLLECTED_BY_CUSTOMER';

UPDATE youdash_order_timeline_events SET status = 'BOOKED' WHERE status IN ('CREATED', 'ORDER_CREATED');
UPDATE youdash_order_timeline_events SET status = 'RIDER_ASSIGNED' WHERE status IN ('CONFIRMED', 'PICKUP_CONFIRMED');
UPDATE youdash_order_timeline_events SET status = 'PICKED_UP' WHERE status = 'PARCEL_PICKED_UP';
UPDATE youdash_order_timeline_events SET status = 'AT_ORIGIN_HUB' WHERE status = 'ARRIVED_ORIGIN_HUB';
UPDATE youdash_order_timeline_events SET status = 'IN_TRANSIT' WHERE status IN ('DISPATCHED_TO_DESTINATION', 'DEPARTED_ORIGIN_HUB');
UPDATE youdash_order_timeline_events SET status = 'AT_DESTINATION_HUB' WHERE status IN ('ARRIVED_DESTINATION_HUB', 'SORTED_AT_DESTINATION');
UPDATE youdash_order_timeline_events SET status = 'OUT_FOR_DELIVERY' WHERE status = 'DELIVERY_RIDER_ASSIGNED';
UPDATE youdash_order_timeline_events SET status = 'AWAITING_HUB_COLLECTION' WHERE status = 'READY_FOR_PICKUP';
UPDATE youdash_order_timeline_events SET status = 'COLLECTED' WHERE status = 'COLLECTED_BY_CUSTOMER';

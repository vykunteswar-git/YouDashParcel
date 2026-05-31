-- Outstation spec: use PICKUP_ASSIGNED for pickup-rider assignment (D2D / D2H).
-- Incity orders keep RIDER_ASSIGNED.

UPDATE orders
   SET status = 'PICKUP_ASSIGNED'
 WHERE service_mode = 'OUTSTATION'
   AND status = 'RIDER_ASSIGNED'
   AND delivery_type IN ('DOOR_TO_DOOR', 'DOOR_TO_HUB');

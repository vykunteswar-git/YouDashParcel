# Zone inactive booking rules

## Behaviour

1. **In-city (INCITY)** — pickup and drop must be in the same **active** zone.
2. **Paused local area** — pickup and drop in the same **inactive** zone → quote returns `serviceUnavailable: true` with a clear message; order create fails with the same message.
3. **Cross-city** — pickup and drop in different zones (e.g. inactive VSKP → active or inactive Hyderabad) → outstation quote using hubs linked to each point's zone (active zone preferred, else inactive zone at that point).
4. **Outside all zones** — nearest **active** hubs within 50 km (fallback).

## API

- `POST /orders/quote`
- `POST /orders` (create)
- `POST /orders/calculate-final`

Hubs must have `zone_id` set for zone-linked outstation matching on order create.

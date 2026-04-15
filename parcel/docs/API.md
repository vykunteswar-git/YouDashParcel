# YouDash Parcel — HTTP API reference

Backend: Spring Boot (`parcel`). Default base URL: `http://<host>:8080` (no global context path). Responses are usually wrapped in `ApiResponse<T>` (`data`, `message`, `messageKey`, `status`, `totalCount`, `success`).

**Interactive docs:** Swagger UI (`/swagger-ui.html`) and OpenAPI JSON (`/v3/api-docs`) are enabled by default (override with Spring profile `prod` if needed).

---

## Table of contents

1. [Authentication](#1-authentication)
2. [Public endpoints](#2-public-endpoints-no-jwt)
3. [Customer & UX APIs](#3-customer--ux-apis-jwt)
4. [Users, orders, payments](#4-users-orders-payments-jwt)
5. [Riders](#5-riders-jwt)
6. [Admin APIs](#6-admin-apis-jwt--admin-only)
7. [DTO field reference](#7-dto-field-reference)
8. [Typical flows](#8-typical-flows)
9. [Cross-cutting behavior](#9-cross-cutting-behavior)

---

## 1. Authentication

| Item | Detail |
|------|--------|
| Header | `Authorization: Bearer <jwt>` |
| JWT claims | `id` (Long), `type` (`USER` or `ADMIN`) |
| Admin routes | Paths under `/admin/**` except `POST /admin/login` require `type == ADMIN` (403 otherwise). |
| Rider actions | No separate `RIDER` JWT is issued in code. Customer login returns `USER`. `RiderAccessVerifier` resolves the acting rider from token `id` (if it is a rider PK) or from a **user whose phone matches** a `RiderEntity`. |

**Public (no JWT):** `/auth/**`, `POST /admin/login`, `/public/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`.

---

## 2. Public endpoints (no JWT)

| Method | Path | Purpose | Service |
|--------|------|---------|---------|
| POST | `/auth/send-otp` | Request OTP for phone | `AuthService` |
| POST | `/auth/verify-otp` | Verify OTP; returns `UserResponseDTO` with JWT | `AuthService` |
| POST | `/admin/login` | Admin email/password → JWT | `AdminService` |
| GET | `/public/vehicles` | Active vehicles for UI | `AdminService.getActiveVehicles` |
| GET | `/public/categories` | Active package categories | `AdminService.getActiveCategories` |

---

## 3. Customer & UX APIs (JWT)

| Method | Path | Purpose | Service |
|--------|------|---------|---------|
| POST | `/api/pricing/calculate` | Unified incity/outstation fare quote | `PricingCalculateService` |
| POST | `/api/service/availability` | Service mode (INCITY/OUTSTATION), zones, vehicles/hubs, nearest hubs | `ServiceAvailabilityService` |
| GET | `/api/delivery/options` | Active delivery option codes (incity + outstation lists) | `DeliveryOptionService` |
| POST | `/api/incity/vehicles/estimate` | Estimated total per active vehicle (same zone) | `LogisticsUxService` |
| POST | `/api/routes/preview` | Hub path / cities for pickup→drop | `LogisticsUxService` |
| GET | `/api/routes/serviceable-cities` | Cities that have an active hub | `LogisticsUxService` |
| GET | `/api/hubs/nearest?lat=&lng=` | Nearest active hub + distance | `LogisticsUxService` |

---

## 4. Users, orders, payments (JWT)

| Method | Path | Purpose | Service |
|--------|------|---------|---------|
| POST | `/users` | Create user | `UserService` |
| GET | `/users` | List users | `UserService` |
| GET | `/users/{id}` | User by id | `UserService` |
| GET | `/users/phone/{phone}` | User by phone | `UserService` |
| PUT | `/users/{id}` | Update user | `UserService` |
| DELETE | `/users/{id}` | Soft delete | `UserService` |
| GET | `/package-items/{categoryId}` | Package items for category | `PackageItemService` |
| POST | `/orders` | Create order (pricing via `PricingCalculateService`, distance via `DistanceService`) | `OrderService` |
| GET | `/orders/user/{userId}` | Orders for user | `OrderService` |
| GET | `/orders/{id}` | Order detail | `OrderService` |
| GET | `/orders/{id}/tracking` | Tracking snapshot | `OrderService` |
| PUT | `/orders/{id}/cancel` | Cancel order | `OrderService` |
| POST | `/payments/create-order` | Create Razorpay order (amount from DB) | `PaymentService` |
| POST | `/payments/verify` | Verify Razorpay signature; mark paid | `PaymentService` |
| POST | `/payments/webhook` | Razorpay webhook (`X-Razorpay-Signature`) | `PaymentService` |

---

## 5. Riders (JWT)

| Method | Path | Purpose | Notes |
|--------|------|---------|--------|
| POST | `/riders/orders/{orderId}/accept` | Accept order | `RiderAccessVerifier.resolveActingRiderId` |
| POST | `/riders/orders/{orderId}/reject` | Reject | |
| PUT | `/riders/orders/{orderId}/status` | Body: `RiderOrderStatusRequestDTO` | |
| POST | `/riders/fcm-token` | Body: `FcmTokenRequestDTO` | Saves token on rider |
| POST | `/riders` | Register rider | |
| GET | `/riders` | List riders | |
| GET | `/riders/available` | Available riders | |
| GET | `/riders/{id}/orders` | Orders for rider | `canAccessRider` (admin / same rider / user phone match) |
| PUT | `/riders/{id}/availability` | JSON `{"isAvailable": boolean}` | |
| PUT | `/riders/{id}/location` | JSON `{"lat", "lng"}` | |

**Service:** `RiderService`, `OrderService`.

---

## 6. Admin APIs (JWT + ADMIN only)

### Core admin (`/admin`)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/admin/vehicles` | Create vehicle |
| GET | `/admin/vehicles` | List vehicles |
| PUT | `/admin/vehicles/{id}` | Update vehicle |
| POST | `/admin/notifications/test` | Send a test push to a device token |
| POST | `/admin/categories` | Create category |
| GET | `/admin/categories` | All categories |
| GET | `/admin/categories/active` | Active categories |
| PUT | `/admin/categories/{id}` | Update category |
| PUT | `/admin/categories/{id}/toggle` | Toggle active |
| POST | `/admin/package-items` | Create package item |
| GET | `/admin/package-items` | List package items |
| PUT | `/admin/package-items/{id}` | Update |
| PUT | `/admin/package-items/{id}/toggle` | Toggle |

**Service:** `AdminService`.

### Users & riders

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/users` | All users |
| GET | `/admin/riders/pending` | Pending approval |
| POST | `/admin/riders/{id}/approve` | Approve |
| POST | `/admin/riders/{id}/reject` | Reject |
| POST | `/admin/riders/available` | Eligible for assignment |

### Orders

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/orders/unassigned` | Unassigned pickup orders |
| POST | `/admin/orders/{orderId}/assign` | Assign pickup rider (`AssignRiderRequestDTO`) |
| PUT | `/admin/orders/{orderId}/hub-status` | Hub line-haul status (`HubStatusUpdateRequestDTO`) |
| PUT | `/admin/orders/{orderId}/complete-hub-delivery` | Hub segment complete |
| PUT | `/admin/orders/{orderId}/ready-for-delivery` | Ready for last mile |
| POST | `/admin/orders/{orderId}/assign-delivery-rider` | Last-mile rider |

### Zones & zone pricing

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/admin/zones` | Create zone |
| GET | `/admin/zones?city=` | List (optional city filter) |
| GET | `/admin/zones/{id}` | Get zone |
| PUT | `/admin/zones/{id}` | Update |
| DELETE | `/admin/zones/{id}` | Delete zone (+ zone pricing rows) |
| POST | `/admin/pricing/zone` | Create zone pricing |
| GET | `/admin/pricing/zone` | List all |
| PUT | `/admin/pricing/zone/{id}` | Update |
| DELETE | `/admin/pricing/zone/{id}` | Soft delete |

### Hubs & hub routes

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/admin/hubs` | Create hub |
| GET | `/admin/hubs?city=` | List |
| GET | `/admin/hubs/{id}` | Get hub |
| PUT | `/admin/hubs/{id}` | Update |
| DELETE | `/admin/hubs/{id}` | Soft delete |
| PATCH | `/admin/hubs/{id}/status` | `HubStatusPatchDTO` |
| POST | `/admin/routes` | Create hub route |
| GET | `/admin/routes` | List routes |
| PUT | `/admin/routes/{id}` | Update route |
| DELETE | `/admin/routes/{id}` | Soft delete |

### Global & weight pricing

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/config/global` | Active global delivery config |
| POST | `/admin/config/global` | New version (deactivates previous) |
| PUT | `/admin/config/global` | Update active |
| GET | `/admin/pricing/weight` | Active weight pricing |
| POST | `/admin/pricing/weight` | New version |
| PUT | `/admin/pricing/weight` | Update active |

### Delivery options (admin)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/admin/delivery-options` | All rows |
| POST | `/admin/delivery-options` | Create |
| PUT | `/admin/delivery-options/{id}` | Update |
| DELETE | `/admin/delivery-options/{id}` | Delete |

---

## 7. DTO field reference

### Auth & user

| DTO | Fields |
|-----|--------|
| `OtpRequestDTO` | `phoneNumber` |
| `OtpResponseDTO` | `phoneNumber`, `otp` |
| `VerifyOtpRequestDTO` | `phoneNumber`, `otp`, `fcmToken` (optional) |
| `UserRequestDTO` | `phoneNumber`, `firstName`, `lastName`, `email` |
| `UserResponseDTO` | `id`, `phoneNumber`, `firstName`, `lastName`, `email`, `active`, `profileCompleted`, `token` |
| `AdminLoginDTO` | `email`, `password` |
| `AdminResponseDTO` | `id`, `email`, `token` |

### Orders

| DTO | Fields |
|-----|--------|
| `OrderRequestDTO` | `userId`, `pickupAddress`, `deliveryAddress`, `pickupLat`, `pickupLng`, `deliveryLat`, `deliveryLng`, `senderName`, `senderPhone`, `receiverName`, `receiverPhone`, `packageCategoryId`, `description`, `weight`, `imageUrl`, `vehicleTypeId` (incity), `deliveryOption` (outstation: `DOOR_TO_DOOR` / `DOOR_TO_HUB` / `HUB_TO_DOOR`), `distanceKm`, `paymentType`, `scheduledDate`, `timeSlot`, `packageItemIds` |
| `OrderResponseDTO` | `id`, `orderId`, `userId`, addresses & coords, sender/receiver, `packageCategoryId`, `packageCategoryName`, `description`, `weight`, `imageUrl`, `vehicleTypeId`, `vehicleName`, `distanceKm`, `totalAmount`, `baseAmount`, `platformFee`, `deliveryFee`, `discountAmount`, `gstAmount`, `pricePerKmUsed`, `paymentType`, `paymentStatus`, `paymentMethod`, `paymentCreatedAt`, `paymentUpdatedAt`, `razorpayOrderId`, `razorpayPaymentId`, `status`, `riderId`, `deliveryRiderId`, `fulfillmentType`, `scheduledDate`, `timeSlot`, `createdAt`, `packageItemIds`, `packageItemNames` |
| `OrderTrackingDTO` | `orderId`, `orderPublicId`, `status`, `riderId`, `riderName`, `riderPhone`, `riderLat`, `riderLng`, `updatedAt` |

### Pricing & availability

| DTO | Fields |
|-----|--------|
| `PricingCalculateRequestDTO` | `pickupLat`, `pickupLng`, `dropLat`, `dropLng`, `vehicleId` (incity), `weightKg`, `deliveryOption` (outstation), `sourceHubId`, `destinationHubId` |
| `PricingCalculateResponseDTO` | `serviceMode`, `straightLineDistanceKm`, `deliveryOption`, `firstMileCost`, `hubRouteCost`, `lastMileCost`, `originHubId`, `destinationHubId`, `firstMileDistanceKm`, `lastMileDistanceKm`, `firstMileRatePerKm`, `lastMileRatePerKm`, `vehicleCost`, `weightCost`, `hubTransportCost`, `vehicleComponent`, `zoneComponent`, `firstMileComponent`, `interHubComponent`, `lastMileComponent`, `weightCharge`, `subTotalBeforeMin`, `minimumChargeApplied`, `subTotalAfterMin`, `platformFee`, `gstPercent`, `gstAmount`, `totalAmount`, `hubPathIds`, `route`, `breakdownLines` |
| `ServiceAvailabilityRequestDTO` | `pickupLat`, `pickupLng`, `dropLat`, `dropLng`, `weightKg` (required, &gt; 0) |
| `ServiceAvailabilityResponseDTO` | `serviceMode`, `pickupZoneId`, `dropZoneId`, `servingZoneId`, `straightLineDistanceKm`, `vehicles`, `hubs`, `isServiceable`, `nearestPickupHub`, `nearestDropHub`, `deliveryOptions` |
| `VehicleAvailabilityDTO` | `id`, `name`, `maxWeight`, `pricePerKm`, `baseFare`, `minimumKm` |
| `HubAvailabilityDTO` | `id`, `city`, `name`, `lat`, `lng` |
| `NearestHubDTO` | `id`, `city`, `name`, `lat`, `lng`, `distanceKm` |
| `OutstationDeliveryOptionDTO` | `type` (`DOOR_TO_DOOR` / `DOOR_TO_HUB` / `HUB_TO_DOOR`), `title`, `description` |
| `IncityVehicleEstimateRequestDTO` | `pickupLat`, `pickupLng`, `dropLat`, `dropLng`, `weightKg` |
| `VehiclePriceEstimateDTO` | `vehicleId`, `vehicleName`, `totalAmount` |
| `RoutePreviewRequestDTO` | `pickupLat`, `pickupLng`, `dropLat`, `dropLng` |
| `RoutePreviewResponseDTO` | `serviceMode`, `cities`, `hubPathIds` |
| `NearestHubResponseDTO` | `hubId`, `city`, `name`, `lat`, `lng`, `distanceKm` |

### Payments

| DTO | Fields |
|-----|--------|
| `RazorpayCreateOrderRequestDTO` | `orderId` (internal id or `YP-...` reference) |
| `RazorpayOrderCreatedDTO` | `razorpayOrderId`, `amount` (paise), `currency`, `keyId` |
| `RazorpayVerifyPaymentRequestDTO` | `orderId`, `razorpayOrderId`, `razorpayPaymentId`, `razorpaySignature` |

### Riders

| DTO | Fields |
|-----|--------|
| `RiderRequestDTO` | `name`, `phone`, `vehicleId` (preferred), `vehicleType` (legacy fallback), `emergencyPhone`, `profileImageUrl`, `aadhaarImageUrl`, `licenseImageUrl` |
| `RiderResponseDTO` | `id`, `name`, `phone`, `vehicleType`, `isAvailable`, `rating`, `approvalStatus` |
| `RiderOrderStatusRequestDTO` | `status` |
| `AssignRiderRequestDTO` | `riderId` |
| `HubStatusUpdateRequestDTO` | `status` |
| `FcmTokenRequestDTO` | `token` |

### Catalog (admin / public)

| DTO | Fields |
|-----|--------|
| `VehicleDTO` | `id`, `name`, `pricePerKm`, `baseFare`, `minimumKm`, `maxWeight`, `imageUrl`, `isActive` |
| `PackageCategoryDTO` | `id`, `name`, `imageUrl`, `defaultDescription`, `isActive` |
| `PackageItemDTO` | `id`, `name`, `imageUrl`, `packageCategoryId`, `isActive` |
| `PackageItemResponseDTO` | `id`, `name`, `imageUrl` |

### Admin zones, hubs, routes, config

| DTO | Fields |
|-----|--------|
| `ZoneRequestDTO` | `city`, `name`, `centerLat`, `centerLng`, `radiusKm`, `isActive` |
| `ZoneResponseDTO` | `id`, `city`, `name`, `centerLat`, `centerLng`, `radiusKm`, `isActive`, `createdAt`, `updatedAt` |
| `ZonePricingRequestDTO` | `zoneId`, `pickupRatePerKm`, `deliveryRatePerKm`, `baseFare` |
| `ZonePricingResponseDTO` | `id`, `zoneId`, `pickupRatePerKm`, `deliveryRatePerKm`, `baseFare`, `isActive`, `createdAt`, `updatedAt` |
| `HubRequestDTO` | `city`, `name`, `lat`, `lng`, `isActive` |
| `HubResponseDTO` | `id`, `city`, `name`, `lat`, `lng`, `isActive`, `createdAt`, `updatedAt` |
| `HubStatusPatchDTO` | `isActive` |
| `HubRouteRequestDTO` | `sourceHubId`, `destinationHubId`, `pricePerKm`, `fixedPrice` |
| `HubRouteResponseDTO` | `id`, `sourceHubId`, `destinationHubId`, `pricePerKm`, `fixedPrice`, `isActive`, `createdAt`, `updatedAt` |
| `GlobalDeliveryConfigDTO` | `id` (read-only), `incityExtensionKm`, `incityExtraRatePerKm`, `baseFare`, `minimumCharge`, `gstPercent`, `platformFee`, `firstMileRatePerKm`, `lastMileRatePerKm`, `createdAt`, `updatedAt` (read-only) |
| `WeightPricingConfigDTO` | `id` (read-only), `type` (e.g. `PER_KG`), `rate`, `createdAt`, `updatedAt` (read-only) |
| `DeliveryOptionRequestDTO` | `category`, `code`, `sortOrder`, `isActive` |
| `DeliveryOptionsResponseDTO` | `incityOptions`, `outstationOptions` (lists of strings) |
| `DeliveryOptionAdminResponseDTO` | `id`, `category`, `code`, `sortOrder`, `isActive`, `createdAt`, `updatedAt` |

---

## 8. Typical flows

1. **Customer (incity):** `GET /public/categories` & `GET /public/vehicles` → `POST /auth/send-otp` → `POST /auth/verify-otp` → optional `POST /api/service/availability`, `POST /api/pricing/calculate`, `POST /api/incity/vehicles/estimate` → `POST /orders` → `POST /payments/create-order` → Razorpay Checkout → `POST /payments/verify`.
2. **Customer (outstation):** Same auth → `GET /api/delivery/options` → `POST /api/routes/preview` → `POST /api/pricing/calculate` → `POST /orders` → payments as above.
3. **Rider:** Log in with same phone as rider record (OTP) → `POST /riders/orders/{id}/accept` / status updates → `PUT /riders/{id}/location`, `PUT /riders/{id}/availability`, `POST /riders/fcm-token`.
4. **Admin:** `POST /admin/login` → configure zones, hub routes, global config → `GET /admin/orders/unassigned` → assign riders → hub status updates as needed.

---

## 9. Cross-cutting behavior

### Distance

- Bean `DistanceService`: **Google Distance Matrix (driving)** when `youdash.maps.distance.api-key` is set; otherwise **haversine** (great-circle) km. See `DistanceConfiguration`.

### OTP (development)

- `AuthServiceImpl` uses a random OTP; test phone `9999999999` gets fixed OTP `1234`. OTP is logged to console; SMS integration is noted as future work.

### Razorpay webhook and security

- `SecurityConfig` permits only `/auth/**`, `/admin/login`, `/public/**`, and Swagger paths without JWT.
- **`POST /payments/webhook` currently requires a JWT** like other protected routes. Razorpay servers do not send your app JWT. For production, either add a `permitAll` rule for `/payments/webhook` (and validate signature only) or terminate webhooks behind a trusted edge.

---

*Generated for the YouDash Parcel codebase. Regenerate or extend when controllers/DTOs change.*

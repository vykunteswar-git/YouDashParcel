package com.youdash.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.notification.NotificationType;
import com.youdash.repository.RiderRepository;

/**
 * Order lifecycle FCM: keeps controllers thin; call from {@link com.youdash.service.impl.OrderServiceImpl}
 * and {@link com.youdash.service.impl.PaymentServiceImpl}.
 */
@Service
public class OrderNotificationService {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationDedupService notificationDedupService;

    @Autowired
    private RiderRepository riderRepository;

    public void onOrderCreated(OrderEntity order, boolean cod) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        if (cod) {
            notificationService.sendToUser(
                    order.getUserId(),
                    "Order placed",
                    "Your order was placed successfully.",
                    NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.ORDER_PLACED_COD),
                    NotificationType.ORDER_PLACED_COD);
            notificationService.sendToAdminDevices(
                    "New COD order",
                    "Order #" + order.getId() + " — COD, ready for assignment.",
                    NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.ADMIN_COD_ORDER),
                    NotificationType.ADMIN_COD_ORDER);
        }
    }

    public void onPaymentSuccessFirstTime(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String key = "payment_success:" + order.getId();
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Payment successful",
                "Payment successful — your order is confirmed.",
                NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.PAYMENT_SUCCESS),
                NotificationType.PAYMENT_SUCCESS);
        notificationService.sendToAdminDevices(
                "Payment received",
                "Order #" + order.getId() + " paid online.",
                NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.ADMIN_PAYMENT_SUCCESS),
                NotificationType.ADMIN_PAYMENT_SUCCESS);
    }

    public void onPaymentFailed(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String key = "payment_failed:" + order.getId();
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Payment failed",
                "Payment failed — please try again.",
                NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.PAYMENT_FAILED),
                NotificationType.PAYMENT_FAILED);
        notificationService.sendToAdminDevices(
                "Payment failed",
                "Order #" + order.getId() + " — payment attempt failed.",
                NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.ADMIN_PAYMENT_FAILED),
                NotificationType.ADMIN_PAYMENT_FAILED);
    }

    public void onPickupRiderAssigned(OrderEntity order) {
        if (order == null) {
            return;
        }
        if (order.getUserId() != null) {
            notificationService.sendToUser(
                    order.getUserId(),
                    "Rider assigned",
                    "A rider has been assigned to your order.",
                    NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.RIDER_ASSIGNED),
                    NotificationType.RIDER_ASSIGNED);
        }
        if (order.getRiderId() != null) {
            notifyRiderJob(order.getRiderId(), order, "New pickup", "You have a new pickup assignment.");
        }
    }

    public void onDeliveryRiderAssigned(OrderEntity order) {
        if (order == null || order.getDeliveryRiderId() == null) {
            return;
        }
        notifyRiderJob(order.getDeliveryRiderId(), order, "New delivery", "You have been assigned a last-mile delivery.");
    }

    private void notifyRiderJob(Long riderId, OrderEntity order, String title, String body) {
        riderRepository.findById(riderId)
                .map(RiderEntity::getFcmToken)
                .filter(t -> t != null && !t.isBlank())
                .ifPresent(token -> notificationService.sendToToken(
                        token,
                        title,
                        body,
                        NotificationService.baseData(order.getId(), order.getStatus(), NotificationType.RIDER_JOB_ASSIGNED),
                        NotificationType.RIDER_JOB_ASSIGNED));
    }

    public void onPickedUp(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String key = "picked_up:" + order.getId();
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Parcel picked up",
                "Your parcel has been picked up.",
                NotificationService.baseData(order.getId(), OrderStatus.PICKED_UP, NotificationType.PICKED_UP),
                NotificationType.PICKED_UP);
    }

    public void onHubStatusUpdate(OrderEntity order, String newStatus) {
        if (order == null || order.getUserId() == null || newStatus == null) {
            return;
        }
        String key = "hub:" + order.getId() + ":" + newStatus;
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        NotificationType type;
        String body;
        switch (newStatus) {
            case OrderStatus.AT_SOURCE_HUB:
                type = NotificationType.AT_SOURCE_HUB;
                body = "Your parcel arrived at the origin hub.";
                break;
            case OrderStatus.IN_TRANSIT_TO_DEST_HUB:
                type = NotificationType.IN_TRANSIT_TO_DEST_HUB;
                body = "Your parcel is on the way to the destination hub.";
                break;
            case OrderStatus.AT_DESTINATION_HUB:
                type = NotificationType.AT_DESTINATION_HUB;
                body = "Your parcel arrived at the destination hub.";
                break;
            default:
                return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Shipment update",
                body,
                NotificationService.baseData(order.getId(), newStatus, type),
                type);
    }

    public void onOutForDelivery(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String key = "out_for_delivery:" + order.getId();
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Out for delivery",
                "Your parcel is out for delivery.",
                NotificationService.baseData(order.getId(), OrderStatus.OUT_FOR_DELIVERY, NotificationType.OUT_FOR_DELIVERY),
                NotificationType.OUT_FOR_DELIVERY);
    }

    public void onDelivered(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String key = "delivered:" + order.getId();
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Delivered",
                "Your order has been delivered.",
                NotificationService.baseData(order.getId(), OrderStatus.DELIVERED, NotificationType.DELIVERED),
                NotificationType.DELIVERED);
    }

    public void onDeliveredAtHub(OrderEntity order) {
        if (order == null || order.getUserId() == null) {
            return;
        }
        String key = "delivered_at_hub:" + order.getId();
        if (!notificationDedupService.tryAcquire(key)) {
            return;
        }
        notificationService.sendToUser(
                order.getUserId(),
                "Arrived at hub",
                "Your parcel is ready for collection at the destination hub.",
                NotificationService.baseData(order.getId(), OrderStatus.DELIVERED_AT_HUB, NotificationType.DELIVERED_AT_HUB),
                NotificationType.DELIVERED_AT_HUB);
    }
}

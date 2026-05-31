package com.youdash.util;

import com.youdash.entity.OrderEntity;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import com.youdash.model.wallet.CodCollectionMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutstationCodPolicyTest {

    @Test
    void d2dDeliveryRiderGetsWalletCreditWhenRiderIdMatchesDeliveryAndPickupRiderIdMissing() {
        OrderEntity order = d2dCodOrder(101L, 202L, null, 202L, 500.0);

        assertTrue(OutstationCodPolicy.isOutstationLastMileRider(order, 202L));
        assertTrue(OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, 202L));
        assertFalse(OutstationCodPolicy.riderHoldsCodCash(order, 202L));
    }

    @Test
    void d2dPickupRiderStillHoldsCodCashWhenPickupRiderIdSet() {
        OrderEntity order = d2dCodOrder(101L, 202L, 101L, 202L, 500.0);

        assertTrue(OutstationCodPolicy.riderHoldsCodCash(order, 101L));
        assertFalse(OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, 101L));
        assertTrue(OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, 202L));
        assertFalse(OutstationCodPolicy.riderHoldsCodCash(order, 202L));
    }

    @Test
    void d2dCodQrPickupRiderDoesNotHoldCash() {
        OrderEntity order = d2dCodOrder(101L, 202L, 101L, 202L, 500.0);
        order.setCodCollectionMode(CodCollectionMode.QR);

        assertFalse(OutstationCodPolicy.riderHoldsCodCash(order, 101L));
        assertTrue(OutstationCodPolicy.riderEarnsWalletCreditWithoutCodCash(order, 202L));
    }

    private static OrderEntity d2dCodOrder(
            Long pickupRiderId, Long deliveryRiderId, Long pickupField, Long riderId, double total) {
        OrderEntity order = new OrderEntity();
        order.setServiceMode(ServiceMode.OUTSTATION);
        order.setDeliveryType("DOOR_TO_DOOR");
        order.setPaymentType(PaymentType.COD);
        order.setCodCollectionMode(CodCollectionMode.CASH);
        order.setCodCollectedAmount(total);
        order.setTotalAmount(total);
        order.setPickupRiderId(pickupField);
        order.setDeliveryRiderId(deliveryRiderId);
        order.setRiderId(riderId);
        return order;
    }
}

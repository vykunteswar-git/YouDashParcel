package com.youdash.config;

import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.repository.DeliveryTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class PricingConfigInitializer {

    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;

    @PostConstruct
    public void init() {
        ensureDeliveryType("STANDARD");
        ensureDeliveryType("EXPRESS");
        ensureDeliveryType("SAFE");
    }

    private void ensureDeliveryType(String name) {
        deliveryTypeRepository.findByNameIgnoreCaseAndActiveTrue(name).orElseGet(() -> {
            DeliveryTypeEntity t = new DeliveryTypeEntity();
            t.setName(name);
            t.setFee(BigDecimal.ZERO);
            t.setActive(true);
            log.info("Creating default delivery type {} (fee 0)", name);
            return deliveryTypeRepository.save(t);
        });
    }
}

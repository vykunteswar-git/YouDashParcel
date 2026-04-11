package com.youdash.config;

import com.youdash.entity.DeliveryOptionEntity;
import com.youdash.model.DeliveryOptionCategory;
import com.youdash.repository.DeliveryOptionRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DeliveryOptionsInitializer {

    @Autowired
    private DeliveryOptionRepository deliveryOptionRepository;

    @PostConstruct
    public void init() {
        if (deliveryOptionRepository.count() > 0) {
            return;
        }
        log.info("Seeding default delivery options (UI keys)");
        seed(DeliveryOptionCategory.INCITY, "STANDARD", 0);
        seed(DeliveryOptionCategory.INCITY, "EXPRESS", 1);
        seed(DeliveryOptionCategory.INCITY, "SAFE", 2);
        seed(DeliveryOptionCategory.OUTSTATION, "DOOR_TO_DOOR", 0);
        seed(DeliveryOptionCategory.OUTSTATION, "HUB_TO_HUB", 1);
        seed(DeliveryOptionCategory.OUTSTATION, "DOOR_TO_HUB", 2);
        seed(DeliveryOptionCategory.OUTSTATION, "HUB_TO_DOOR", 3);
    }

    private void seed(DeliveryOptionCategory category, String code, int sort) {
        DeliveryOptionEntity e = new DeliveryOptionEntity();
        e.setCategory(category);
        e.setCode(code);
        e.setSortOrder(sort);
        e.setIsActive(true);
        deliveryOptionRepository.save(e);
    }
}

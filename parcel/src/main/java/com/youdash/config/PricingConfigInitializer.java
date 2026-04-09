package com.youdash.config;

import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.entity.GstConfigEntity;
import com.youdash.entity.InCityRadiusConfigEntity;
import com.youdash.entity.PlatformFeeEntity;
import com.youdash.repository.DeliveryTypeRepository;
import com.youdash.repository.GstConfigRepository;
import com.youdash.repository.InCityRadiusConfigRepository;
import com.youdash.repository.PlatformFeeRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Slf4j
@Component
public class PricingConfigInitializer {

    @Autowired
    private GstConfigRepository gstConfigRepository;

    @Autowired
    private PlatformFeeRepository platformFeeRepository;

    @Autowired
    private DeliveryTypeRepository deliveryTypeRepository;

    @Autowired
    private InCityRadiusConfigRepository inCityRadiusConfigRepository;

    @PostConstruct
    public void init() {
        gstConfigRepository.findFirstByActiveTrueOrderByIdDesc().orElseGet(() -> {
            GstConfigEntity cfg = new GstConfigEntity();
            cfg.setCgstPercent(BigDecimal.ZERO);
            cfg.setSgstPercent(BigDecimal.ZERO);
            cfg.setActive(true);
            log.info("Creating default GST config (0%,0%)");
            return gstConfigRepository.save(cfg);
        });

        platformFeeRepository.findFirstByActiveTrueOrderByIdDesc().orElseGet(() -> {
            PlatformFeeEntity cfg = new PlatformFeeEntity();
            cfg.setFee(BigDecimal.ZERO);
            cfg.setActive(true);
            log.info("Creating default platform fee (0)");
            return platformFeeRepository.save(cfg);
        });

        inCityRadiusConfigRepository.findFirstByActiveTrueOrderByIdDesc().orElseGet(() -> {
            InCityRadiusConfigEntity cfg = new InCityRadiusConfigEntity();
            cfg.setRadiusKm(BigDecimal.valueOf(60.0));
            cfg.setActive(true);
            log.info("Creating default in-city radius (60km)");
            return inCityRadiusConfigRepository.save(cfg);
        });

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


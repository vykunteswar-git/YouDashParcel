package com.youdash.repository;

import com.youdash.entity.DeliveryTypeEntity;
import com.youdash.entity.DeliveryTypeRateEntity;
import com.youdash.pricing.DeliveryScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeliveryTypeRateRepository extends JpaRepository<DeliveryTypeRateEntity, Long> {
    Optional<DeliveryTypeRateEntity> findByDeliveryTypeAndScopeAndActiveTrue(DeliveryTypeEntity deliveryType, DeliveryScope scope);
}


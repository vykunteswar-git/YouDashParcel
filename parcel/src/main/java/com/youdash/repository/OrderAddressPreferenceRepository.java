package com.youdash.repository;

import com.youdash.entity.OrderAddressPreferenceEntity;
import com.youdash.model.OrderAddressRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderAddressPreferenceRepository extends JpaRepository<OrderAddressPreferenceEntity, Long> {
    List<OrderAddressPreferenceEntity> findByUserId(Long userId);

    Optional<OrderAddressPreferenceEntity> findByUserIdAndRoleAndCoordinateKey(
            Long userId, OrderAddressRole role, String coordinateKey);
}

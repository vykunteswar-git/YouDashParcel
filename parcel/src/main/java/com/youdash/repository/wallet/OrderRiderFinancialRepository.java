package com.youdash.repository.wallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.OrderRiderFinancialEntity;

@Repository
public interface OrderRiderFinancialRepository extends JpaRepository<OrderRiderFinancialEntity, Long> {

    Optional<OrderRiderFinancialEntity> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    List<OrderRiderFinancialEntity> findByOrderIdIn(Collection<Long> orderIds);
}

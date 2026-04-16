package com.youdash.repository.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.RiderCommissionConfigEntity;

@Repository
public interface RiderCommissionConfigRepository extends JpaRepository<RiderCommissionConfigEntity, Long> {
}

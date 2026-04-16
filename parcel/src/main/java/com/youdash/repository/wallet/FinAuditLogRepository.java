package com.youdash.repository.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.FinAuditLogEntity;

@Repository
public interface FinAuditLogRepository extends JpaRepository<FinAuditLogEntity, Long> {
}

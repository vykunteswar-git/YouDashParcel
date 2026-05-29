package com.youdash.repository.wallet;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.CodDepositEntity;

@Repository
public interface CodDepositRepository extends JpaRepository<CodDepositEntity, Long> {

    List<CodDepositEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, org.springframework.data.domain.Pageable pageable);
}

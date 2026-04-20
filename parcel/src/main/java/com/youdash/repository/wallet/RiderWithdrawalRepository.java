package com.youdash.repository.wallet;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.RiderWithdrawalEntity;
import com.youdash.model.wallet.WithdrawalStatus;

@Repository
public interface RiderWithdrawalRepository extends JpaRepository<RiderWithdrawalEntity, Long> {

    List<RiderWithdrawalEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, Pageable pageable);

    List<RiderWithdrawalEntity> findByStatusOrderByCreatedAtDesc(WithdrawalStatus status, Pageable pageable);

    List<RiderWithdrawalEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

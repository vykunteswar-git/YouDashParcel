package com.youdash.repository.wallet;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.RiderWalletTransactionEntity;
import com.youdash.model.wallet.WalletTxnReferenceType;
import com.youdash.model.wallet.WalletTxnStatus;

@Repository
public interface RiderWalletTransactionRepository extends JpaRepository<RiderWalletTransactionEntity, Long> {

    List<RiderWalletTransactionEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, Pageable pageable);

    Optional<RiderWalletTransactionEntity> findTopByRiderIdAndReferenceTypeAndReferenceIdAndStatusOrderByIdDesc(
            Long riderId,
            WalletTxnReferenceType referenceType,
            Long referenceId,
            WalletTxnStatus status
    );
}

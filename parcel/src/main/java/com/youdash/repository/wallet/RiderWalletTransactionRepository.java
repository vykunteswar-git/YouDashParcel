package com.youdash.repository.wallet;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.RiderWalletTransactionEntity;
import com.youdash.model.wallet.WalletTxnReferenceType;
import com.youdash.model.wallet.WalletTxnStatus;

@Repository
public interface RiderWalletTransactionRepository extends JpaRepository<RiderWalletTransactionEntity, Long> {

    List<RiderWalletTransactionEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, Pageable pageable);

    @Query("""
            SELECT t FROM RiderWalletTransactionEntity t
            WHERE t.riderId = :riderId
              AND (t.note IS NULL OR t.note <> :hiddenNote)
            ORDER BY t.createdAt DESC
            """)
    List<RiderWalletTransactionEntity> findRiderVisibleByRiderIdOrderByCreatedAtDesc(
            @Param("riderId") Long riderId,
            @Param("hiddenNote") String hiddenNote,
            Pageable pageable);

    @Query("""
            SELECT t FROM RiderWalletTransactionEntity t
            WHERE t.riderId IN :riderIds
              AND (t.note IS NULL OR t.note <> :hiddenNote)
            ORDER BY t.createdAt DESC
            """)
    List<RiderWalletTransactionEntity> findRiderVisibleByRiderIdInOrderByCreatedAtDesc(
            @Param("riderIds") Collection<Long> riderIds,
            @Param("hiddenNote") String hiddenNote,
            Pageable pageable);

    Optional<RiderWalletTransactionEntity> findTopByRiderIdAndReferenceTypeAndReferenceIdAndStatusOrderByIdDesc(
            Long riderId,
            WalletTxnReferenceType referenceType,
            Long referenceId,
            WalletTxnStatus status
    );
}

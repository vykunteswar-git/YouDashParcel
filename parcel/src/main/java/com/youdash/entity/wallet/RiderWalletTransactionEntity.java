package com.youdash.entity.wallet;

import java.time.Instant;

import com.youdash.model.wallet.WalletTxnReferenceType;
import com.youdash.model.wallet.WalletTxnStatus;
import com.youdash.model.wallet.WalletTxnType;
import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "youdash_rider_wallet_transactions", indexes = {
        @Index(name = "idx_wallet_txn_rider_created", columnList = "rider_id,created_at"),
        @Index(name = "idx_wallet_txn_ref", columnList = "reference_type,reference_id")
})
@Data
public class RiderWalletTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rider_id", nullable = false)
    private Long riderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private WalletTxnType type;

    @Column(name = "amount", nullable = false)
    private Double amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 24)
    private WalletTxnReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WalletTxnStatus status;

    @Column(name = "note", length = 512)
    private String note;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(name = "created_at")
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (status == null) {
            status = WalletTxnStatus.COMPLETED;
        }
    }
}

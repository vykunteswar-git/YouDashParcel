package com.youdash.repository.wallet;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.youdash.entity.wallet.RiderWalletEntity;

import jakarta.persistence.LockModeType;

@Repository
public interface RiderWalletRepository extends JpaRepository<RiderWalletEntity, Long> {

    Optional<RiderWalletEntity> findByRiderId(Long riderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from RiderWalletEntity w where w.riderId = :riderId")
    Optional<RiderWalletEntity> lockByRiderId(@Param("riderId") Long riderId);
}

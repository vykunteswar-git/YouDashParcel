package com.youdash.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.youdash.entity.RiderEntity;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {
    List<RiderEntity> findByIsAvailableTrue();

    List<RiderEntity> findByIsAvailableFalse();

    List<RiderEntity> findByApprovalStatusOrderByCreatedAtDesc(String approvalStatus);

    Optional<RiderEntity> findByPhone(String phone);

    Optional<RiderEntity> findByEmailIgnoreCase(String email);

    Optional<RiderEntity> findByPublicId(String publicId);

    Optional<RiderEntity> findByFcmToken(String fcmToken);

    long countByApprovalStatusAndIsAvailableTrue(String approvalStatus);

    @Modifying
    @Query("""
            update RiderEntity r
               set r.isAvailable = false
             where r.id = :riderId
               and (r.isBlocked is null or r.isBlocked = false)
               and r.isAvailable = true
            """)
    int reserveIfAvailable(@Param("riderId") Long riderId);

    @Modifying
    @Query("""
            update RiderEntity r
               set r.isAvailable = true
             where r.id = :riderId
            """)
    int release(@Param("riderId") Long riderId);
}

package com.youdash.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.RiderEntity;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {
    List<RiderEntity> findByIsAvailableTrue();

    List<RiderEntity> findByApprovalStatusOrderByCreatedAtDesc(String approvalStatus);

    Optional<RiderEntity> findByPhone(String phone);

    Optional<RiderEntity> findByPublicId(String publicId);

    Optional<RiderEntity> findByFcmToken(String fcmToken);
}

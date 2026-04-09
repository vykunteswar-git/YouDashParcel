package com.youdash.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.youdash.entity.RiderEntity;

@Repository
public interface RiderRepository extends JpaRepository<RiderEntity, Long> {
    List<RiderEntity> findByIsAvailableTrue();

    Optional<RiderEntity> findByFcmToken(String fcmToken);
}

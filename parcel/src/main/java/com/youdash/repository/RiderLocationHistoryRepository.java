package com.youdash.repository;

import com.youdash.entity.RiderLocationHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RiderLocationHistoryRepository extends JpaRepository<RiderLocationHistoryEntity, Long> {

    Optional<RiderLocationHistoryEntity> findTopByOrderIdOrderByTsDesc(Long orderId);

    List<RiderLocationHistoryEntity> findByOrderIdOrderByTsAsc(Long orderId);
}

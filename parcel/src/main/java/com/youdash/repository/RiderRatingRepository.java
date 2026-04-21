package com.youdash.repository;

import com.youdash.entity.RiderRatingEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RiderRatingRepository extends JpaRepository<RiderRatingEntity, Long> {
    Optional<RiderRatingEntity> findByOrderId(Long orderId);

    List<RiderRatingEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    long countByRiderId(Long riderId);

    long countByRiderIdAndStars(Long riderId, Integer stars);
}

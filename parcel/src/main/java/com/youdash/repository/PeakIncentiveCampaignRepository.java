package com.youdash.repository;

import com.youdash.entity.PeakIncentiveCampaignEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface PeakIncentiveCampaignRepository extends JpaRepository<PeakIncentiveCampaignEntity, Long> {
    List<PeakIncentiveCampaignEntity> findByIsActiveTrueAndValidFromLessThanEqualAndValidToGreaterThanEqual(
            Instant fromInclusive,
            Instant toInclusive);
}

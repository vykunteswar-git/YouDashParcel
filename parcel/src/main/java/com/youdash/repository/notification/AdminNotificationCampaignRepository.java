package com.youdash.repository.notification;

import com.youdash.entity.notification.AdminNotificationCampaignEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AdminNotificationCampaignRepository extends JpaRepository<AdminNotificationCampaignEntity, Long> {
    List<AdminNotificationCampaignEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}


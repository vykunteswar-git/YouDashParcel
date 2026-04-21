package com.youdash.repository.notification;

import com.youdash.entity.notification.NotificationInboxEntity;
import com.youdash.model.notification.NotificationRecipientType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationInboxRepository extends JpaRepository<NotificationInboxEntity, Long> {
    List<NotificationInboxEntity> findByRecipientTypeAndRecipientIdOrderByCreatedAtDesc(
            NotificationRecipientType recipientType,
            Long recipientId,
            Pageable pageable);

    Optional<NotificationInboxEntity> findByIdAndRecipientTypeAndRecipientId(
            Long id,
            NotificationRecipientType recipientType,
            Long recipientId);

    long countByRecipientTypeAndRecipientIdAndIsReadFalse(NotificationRecipientType recipientType, Long recipientId);

    @Modifying
    @Query("""
            update NotificationInboxEntity n
               set n.isRead = true,
                   n.readAt = :readAt
             where n.recipientType = :recipientType
               and n.recipientId = :recipientId
               and n.isRead = false
            """)
    int markAllRead(
            @Param("recipientType") NotificationRecipientType recipientType,
            @Param("recipientId") Long recipientId,
            @Param("readAt") Instant readAt);
}

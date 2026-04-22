package com.youdash.repository;

import com.youdash.entity.RiderOnlineSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RiderOnlineSessionRepository extends JpaRepository<RiderOnlineSessionEntity, Long> {
    Optional<RiderOnlineSessionEntity> findFirstByRiderIdAndEndedAtIsNullOrderByStartedAtDesc(Long riderId);

    @Query("""
            select s
              from RiderOnlineSessionEntity s
             where s.riderId = :riderId
               and s.startedAt < :windowEnd
               and (s.endedAt is null or s.endedAt > :windowStart)
             order by s.startedAt desc
            """)
    List<RiderOnlineSessionEntity> findSessionsOverlappingWindow(
            @Param("riderId") Long riderId,
            @Param("windowStart") Instant windowStart,
            @Param("windowEnd") Instant windowEnd);
}

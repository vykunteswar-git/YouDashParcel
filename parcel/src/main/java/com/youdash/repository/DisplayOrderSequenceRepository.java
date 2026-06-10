package com.youdash.repository;

import com.youdash.entity.DisplayOrderSequenceEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DisplayOrderSequenceRepository extends JpaRepository<DisplayOrderSequenceEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from DisplayOrderSequenceEntity s where s.id = :id")
    Optional<DisplayOrderSequenceEntity> findByIdForUpdate(@Param("id") Long id);
}

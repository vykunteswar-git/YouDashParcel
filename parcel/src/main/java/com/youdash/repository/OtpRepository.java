package com.youdash.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.youdash.entity.OtpEntity;

public interface OtpRepository extends JpaRepository<OtpEntity, Long> {

  Optional<OtpEntity> findByPhoneNumber(String phoneNumber);

  Optional<OtpEntity> findTopByPhoneNumberOrderByIdDesc(String phoneNumber);
}
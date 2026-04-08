package com.youdash.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.youdash.entity.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByPhoneNumber(String phoneNumber);

  List<UserEntity> findByActiveTrue();

  Optional<UserEntity> findByFcmToken(String fcmToken);
}
package com.youdash.repository;

import com.youdash.entity.AppVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppVersionRepository extends JpaRepository<AppVersionEntity, Long> {
}

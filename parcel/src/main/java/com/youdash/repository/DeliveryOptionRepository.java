package com.youdash.repository;

import com.youdash.entity.DeliveryOptionEntity;
import com.youdash.model.DeliveryOptionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryOptionRepository extends JpaRepository<DeliveryOptionEntity, Long> {

    List<DeliveryOptionEntity> findByCategoryAndIsActiveTrueOrderBySortOrderAsc(DeliveryOptionCategory category);

    List<DeliveryOptionEntity> findAllByOrderByCategoryAscSortOrderAsc();

    boolean existsByCategoryAndCode(DeliveryOptionCategory category, String code);

    boolean existsByCategoryAndCodeAndIdNot(DeliveryOptionCategory category, String code, Long id);
}

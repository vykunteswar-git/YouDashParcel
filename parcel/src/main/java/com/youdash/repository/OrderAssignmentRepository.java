package com.youdash.repository;

import com.youdash.entity.OrderAssignmentEntity;
import com.youdash.model.OrderAssignmentRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OrderAssignmentRepository extends JpaRepository<OrderAssignmentEntity, Long> {
    List<OrderAssignmentEntity> findByOrderIdOrderByAssignedAtAsc(Long orderId);

    Optional<OrderAssignmentEntity> findFirstByOrderIdAndAssignmentRoleAndIsActiveTrue(
            Long orderId,
            OrderAssignmentRole assignmentRole);
}

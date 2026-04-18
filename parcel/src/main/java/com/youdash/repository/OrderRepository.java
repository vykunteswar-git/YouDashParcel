package com.youdash.repository;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    long countByRiderIdAndStatus(Long riderId, OrderStatus status);

    List<OrderEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    List<OrderEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, Pageable pageable);

    List<OrderEntity> findByRiderIdAndServiceModeAndStatusIn(Long riderId, ServiceMode serviceMode, List<OrderStatus> statuses);

    @Modifying
    @Query("""
            update OrderEntity o
               set o.riderId = :riderId,
                   o.status = :newStatus,
                   o.acceptedAt = :acceptedAt,
                   o.paymentDueAt = :paymentDueAt,
                   o.paymentStatus = :paymentStatus
             where o.id = :orderId
               and o.serviceMode = :serviceMode
               and o.status = :expectedStatus
               and (o.searchExpiresAt is null or o.searchExpiresAt > :now)
            """)
    int tryAcceptOrder(
            @Param("orderId") Long orderId,
            @Param("riderId") Long riderId,
            @Param("serviceMode") ServiceMode serviceMode,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus,
            @Param("acceptedAt") Instant acceptedAt,
            @Param("paymentDueAt") Instant paymentDueAt,
            @Param("paymentStatus") String paymentStatus,
            @Param("now") Instant now);

    /**
     * INCITY + COD: after rider accepts, confirm immediately (no online payment window).
     */
    @Modifying
    @Query("""
            update OrderEntity o
               set o.riderId = :riderId,
                   o.status = :newStatus,
                   o.acceptedAt = :acceptedAt,
                   o.paymentDueAt = null,
                   o.paymentStatus = :paymentStatus
             where o.id = :orderId
               and o.serviceMode = :serviceMode
               and o.paymentType = :paymentType
               and o.status = :expectedStatus
               and (o.searchExpiresAt is null or o.searchExpiresAt > :now)
            """)
    int tryAcceptIncityCodOrder(
            @Param("orderId") Long orderId,
            @Param("riderId") Long riderId,
            @Param("serviceMode") ServiceMode serviceMode,
            @Param("paymentType") PaymentType paymentType,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus,
            @Param("acceptedAt") Instant acceptedAt,
            @Param("paymentStatus") String paymentStatus,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update OrderEntity o
               set o.status = :newStatus
             where o.id = :orderId
               and o.status = :expectedStatus
               and (o.paymentDueAt is null or o.paymentDueAt > :now)
            """)
    int tryMarkPaymentPending(
            @Param("orderId") Long orderId,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus,
            @Param("now") Instant now);

    @Modifying
    @Query("""
            update OrderEntity o
               set o.status = :newStatus,
                   o.cancelReason = :cancelReason,
                   o.paymentStatus = :paymentStatus
             where o.id = :orderId
               and o.status = :expectedStatus
               and o.serviceMode = :serviceMode
            """)
    int updateStatusWithReason(
            @Param("orderId") Long orderId,
            @Param("serviceMode") ServiceMode serviceMode,
            @Param("expectedStatus") OrderStatus expectedStatus,
            @Param("newStatus") OrderStatus newStatus,
            @Param("cancelReason") String cancelReason,
            @Param("paymentStatus") String paymentStatus);

    List<OrderEntity> findByServiceModeAndStatusAndSearchExpiresAtBefore(ServiceMode serviceMode, OrderStatus status, Instant now);

    List<OrderEntity> findByServiceModeAndStatusInAndPaymentDueAtBefore(ServiceMode serviceMode, List<OrderStatus> statuses, Instant now);

    @Query("""
            select o from OrderEntity o
             where o.serviceMode = :serviceMode
               and o.paymentType = :paymentType
               and o.status in :statuses
               and o.paymentDueAt is not null
               and o.paymentDueAt < :now
            """)
    List<OrderEntity> findByServiceModeAndPaymentTypeAndStatusInAndPaymentDueAtBefore(
            @Param("serviceMode") ServiceMode serviceMode,
            @Param("paymentType") PaymentType paymentType,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("now") Instant now);

    boolean existsByRiderIdAndServiceModeAndStatusIn(Long riderId, ServiceMode serviceMode, List<OrderStatus> statuses);

    /** True if the rider has any order in one of the given statuses (any service mode). */
    boolean existsByRiderIdAndStatusIn(Long riderId, List<OrderStatus> statuses);

    @Modifying
    @Query("""
            update OrderEntity o
               set o.paymentStatus = :paymentStatus,
                   o.status = :newStatus,
                   o.razorpayPaymentId = :razorpayPaymentId,
                   o.paymentMethod = :paymentMethod,
                   o.paymentCreatedAt = :now,
                   o.paymentUpdatedAt = :now
             where o.id = :orderId
               and o.serviceMode = :serviceMode
               and o.status in :expectedStatuses
               and (o.paymentStatus is null or upper(o.paymentStatus) <> 'PAID')
            """)
    int markPaidAndConfirm(
            @Param("orderId") Long orderId,
            @Param("serviceMode") ServiceMode serviceMode,
            @Param("expectedStatuses") List<OrderStatus> expectedStatuses,
            @Param("newStatus") OrderStatus newStatus,
            @Param("paymentStatus") String paymentStatus,
            @Param("razorpayPaymentId") String razorpayPaymentId,
            @Param("paymentMethod") String paymentMethod,
            @Param("now") Instant now);

    Optional<OrderEntity> findByDisplayOrderId(String displayOrderId);

    Optional<OrderEntity> findByRazorpayOrderId(String razorpayOrderId);
}

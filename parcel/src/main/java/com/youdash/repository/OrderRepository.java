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

    /** User history feed excluding transiently expired search attempts. */
    List<OrderEntity> findByUserIdAndStatusNotOrderByCreatedAtDesc(Long userId, OrderStatus status);

    /** Newest first; use with {@link Pageable} to cap scans for address suggestions. */
    List<OrderEntity> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    long countByRiderIdAndStatus(Long riderId, OrderStatus status);

    /** Counts DELIVERED orders for a rider across incity (riderId) and outstation split (pickupRiderId / deliveryRiderId). */
    @Query("""
            SELECT COUNT(o) FROM OrderEntity o
            WHERE o.status = :status
              AND (o.riderId = :riderId OR o.pickupRiderId = :riderId OR o.deliveryRiderId = :riderId)
            """)
    long countDeliveredByRiderAnyField(@Param("riderId") Long riderId, @Param("status") OrderStatus status);

    List<OrderEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId);

    List<OrderEntity> findByRiderIdOrderByCreatedAtDesc(Long riderId, Pageable pageable);

    /**
     * Rider feed: incity (riderId) + outstation split (pickupRiderId / deliveryRiderId), newest first.
     * Supports optional date range and pagination.
     */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE (o.riderId = :riderId OR o.pickupRiderId = :riderId OR o.deliveryRiderId = :riderId)
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt < :endDate)
            ORDER BY o.createdAt DESC
            """)
    List<OrderEntity> findRiderOrdersPaged(
            @Param("riderId") Long riderId,
            @Param("startDate") java.time.Instant startDate,
            @Param("endDate") java.time.Instant endDate,
            Pageable pageable);

    @Query("""
            SELECT COUNT(o) FROM OrderEntity o
            WHERE (o.riderId = :riderId OR o.pickupRiderId = :riderId OR o.deliveryRiderId = :riderId)
              AND (:startDate IS NULL OR o.createdAt >= :startDate)
              AND (:endDate IS NULL OR o.createdAt < :endDate)
            """)
    long countRiderOrdersPaged(
            @Param("riderId") Long riderId,
            @Param("startDate") java.time.Instant startDate,
            @Param("endDate") java.time.Instant endDate);

    List<OrderEntity> findByPickupRiderIdOrDeliveryRiderIdOrderByCreatedAtDesc(
            Long pickupRiderId, Long deliveryRiderId, Pageable pageable);

    List<OrderEntity> findByRiderIdAndServiceModeAndStatusIn(Long riderId, ServiceMode serviceMode, List<OrderStatus> statuses);

    /** Latest INCITY order for this rider in one of the given statuses (e.g. active job). */
    Optional<OrderEntity> findFirstByRiderIdAndServiceModeAndStatusInOrderByIdDesc(
            Long riderId, ServiceMode serviceMode, List<OrderStatus> statuses);

    /** Most recent order for this user in any of the provided statuses. */
    Optional<OrderEntity> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<OrderStatus> statuses);
    List<OrderEntity> findByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, List<OrderStatus> statuses);

    /** Latest active order for this rider across service modes. */
    Optional<OrderEntity> findFirstByRiderIdAndStatusInOrderByIdDesc(
            Long riderId, List<OrderStatus> statuses);

    /** Latest active order where rider is primary, pickup, or delivery assignee. */
    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.status IN :statuses
              AND (o.riderId = :riderId OR o.pickupRiderId = :riderId OR o.deliveryRiderId = :riderId)
            ORDER BY o.id DESC
            """)
    List<OrderEntity> findActiveOrdersForRiderAnyRole(
            @Param("riderId") Long riderId,
            @Param("statuses") List<OrderStatus> statuses,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
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
    @Modifying(clearAutomatically = true, flushAutomatically = true)
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

    /**
     * INCITY online orders still in the post-accept payment window, with due time in
     * {@code (now + minRemaining, now + maxRemaining]} — used for a single reminder push.
     */
    @Query("""
            select o from OrderEntity o
             where o.serviceMode = :serviceMode
               and o.paymentType = :paymentType
               and o.status in :statuses
               and o.paymentDueAt is not null
               and o.paymentDueAt > :nowPlusMinRemaining
               and o.paymentDueAt <= :nowPlusMaxRemaining
               and (o.paymentStatus is null or upper(o.paymentStatus) <> 'PAID')
            """)
    List<OrderEntity> findIncityOnlinePaymentDueBetween(
            @Param("serviceMode") ServiceMode serviceMode,
            @Param("paymentType") PaymentType paymentType,
            @Param("statuses") List<OrderStatus> statuses,
            @Param("nowPlusMinRemaining") Instant nowPlusMinRemaining,
            @Param("nowPlusMaxRemaining") Instant nowPlusMaxRemaining);

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

    Optional<OrderEntity> findByUpiQrId(String upiQrId);

    List<OrderEntity> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtAsc(Instant from, Instant to);

    List<OrderEntity> findByPaymentStatusIgnoreCaseAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            String paymentStatus, Instant from, Instant to);

    List<OrderEntity> findByPaymentStatusIgnoreCaseAndPaymentTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
            String paymentStatus, PaymentType paymentType, Instant from, Instant to);

    long countByRiderIdAndStatusAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanEqual(
            Long riderId, OrderStatus status, Instant from, Instant to);

    long countByRiderIdAndStatusAndServiceModeAndUpdatedAtGreaterThanEqualAndUpdatedAtLessThanEqual(
            Long riderId, OrderStatus status, ServiceMode serviceMode, Instant from, Instant to);
}

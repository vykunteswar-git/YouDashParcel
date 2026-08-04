package com.youdash.repository.specification;

import com.youdash.entity.OrderEntity;
import com.youdash.model.OrderStatus;
import com.youdash.model.PaymentType;
import com.youdash.model.ServiceMode;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class OrderSpecifications {
    public static Specification<OrderEntity> filterOrders(
            ServiceMode serviceMode,
            OrderStatus status,
            PaymentType paymentType,
            String assigned,
            Set<Long> originHubIds,
            String pickupTag,
            boolean isOriginPickup,
            Set<Long> destinationHubIds,
            String dropTag,
            boolean isDestinationDrop,
            String q) {
        
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (serviceMode != null) {
                predicates.add(cb.equal(root.get("serviceMode"), serviceMode));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (paymentType != null) {
                predicates.add(cb.equal(root.get("paymentType"), paymentType));
            }

            if ("yes".equalsIgnoreCase(assigned)) {
                predicates.add(cb.or(
                    cb.isNotNull(root.get("riderId")),
                    cb.isNotNull(root.get("pickupRiderId")),
                    cb.isNotNull(root.get("deliveryRiderId"))
                ));
            } else if ("no".equalsIgnoreCase(assigned)) {
                predicates.add(cb.and(
                    cb.isNull(root.get("riderId")),
                    cb.isNull(root.get("pickupRiderId")),
                    cb.isNull(root.get("deliveryRiderId"))
                ));
            }

            // Route: Origin
            if ((originHubIds != null && !originHubIds.isEmpty()) || pickupTag != null || isOriginPickup) {
                List<Predicate> originPredicates = new ArrayList<>();
                if (originHubIds != null && !originHubIds.isEmpty()) {
                    originPredicates.add(root.get("originHubId").in(originHubIds));
                }
                if (pickupTag != null) {
                    originPredicates.add(cb.and(
                        cb.isNull(root.get("originHubId")),
                        cb.like(cb.lower(root.get("pickupTag")), pickupTag.toLowerCase())
                    ));
                }
                if (isOriginPickup) {
                    originPredicates.add(cb.and(
                        cb.isNull(root.get("originHubId")),
                        cb.or(cb.isNull(root.get("pickupTag")), cb.equal(root.get("pickupTag"), ""))
                    ));
                }
                if (!originPredicates.isEmpty()) {
                    predicates.add(cb.or(originPredicates.toArray(new Predicate[0])));
                }
            }

            // Route: Destination
            if ((destinationHubIds != null && !destinationHubIds.isEmpty()) || dropTag != null || isDestinationDrop) {
                List<Predicate> destPredicates = new ArrayList<>();
                if (destinationHubIds != null && !destinationHubIds.isEmpty()) {
                    destPredicates.add(root.get("destinationHubId").in(destinationHubIds));
                }
                if (dropTag != null) {
                    destPredicates.add(cb.and(
                        cb.isNull(root.get("destinationHubId")),
                        cb.like(cb.lower(root.get("dropTag")), dropTag.toLowerCase())
                    ));
                }
                if (isDestinationDrop) {
                    destPredicates.add(cb.and(
                        cb.isNull(root.get("destinationHubId")),
                        cb.or(cb.isNull(root.get("dropTag")), cb.equal(root.get("dropTag"), ""))
                    ));
                }
                if (!destPredicates.isEmpty()) {
                    predicates.add(cb.or(destPredicates.toArray(new Predicate[0])));
                }
            }

            if (q != null && !q.trim().isEmpty()) {
                String searchPattern = "%" + q.trim().toLowerCase() + "%";
                Predicate searchPredicate = cb.or(
                    cb.like(cb.lower(root.get("displayOrderId")), searchPattern),
                    cb.like(cb.lower(root.get("senderName")), searchPattern),
                    cb.like(cb.lower(root.get("receiverName")), searchPattern),
                    cb.like(cb.lower(root.get("senderPhone")), searchPattern),
                    cb.like(cb.lower(root.get("receiverPhone")), searchPattern)
                );
                
                // Also match ID if numeric
                try {
                    Long searchId = Long.parseLong(q.trim());
                    searchPredicate = cb.or(searchPredicate, cb.equal(root.get("id"), searchId));
                } catch (NumberFormatException ignored) {}

                predicates.add(searchPredicate);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}

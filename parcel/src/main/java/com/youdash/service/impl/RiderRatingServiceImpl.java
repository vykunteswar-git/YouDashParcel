package com.youdash.service.impl;

import com.youdash.bean.ApiResponse;
import com.youdash.dto.rating.RiderComplimentStatDTO;
import com.youdash.dto.rating.RiderRatingBreakdownItemDTO;
import com.youdash.dto.rating.RiderRatingRequestDTO;
import com.youdash.dto.rating.RiderRatingSummaryDTO;
import com.youdash.entity.OrderEntity;
import com.youdash.entity.RiderEntity;
import com.youdash.entity.RiderRatingEntity;
import com.youdash.model.OrderStatus;
import com.youdash.repository.OrderRepository;
import com.youdash.repository.RiderRatingRepository;
import com.youdash.repository.RiderRepository;
import com.youdash.service.RiderRatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class RiderRatingServiceImpl implements RiderRatingService {

    @Autowired
    private RiderRatingRepository riderRatingRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RiderRepository riderRepository;

    @Override
    @Transactional
    public ApiResponse<String> submitUserRating(Long orderId, Long userId, RiderRatingRequestDTO dto) {
        ApiResponse<String> response = new ApiResponse<>();
        try {
            if (orderId == null) {
                throw new RuntimeException("orderId is required");
            }
            if (userId == null) {
                throw new RuntimeException("Unauthorized");
            }
            if (dto == null || dto.getStars() == null) {
                throw new RuntimeException("stars is required");
            }
            int stars = dto.getStars();
            if (stars < 1 || stars > 5) {
                throw new RuntimeException("stars must be between 1 and 5");
            }
            OrderEntity order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            if (!Objects.equals(order.getUserId(), userId)) {
                throw new RuntimeException("Access denied");
            }
            if (order.getRiderId() == null) {
                throw new RuntimeException("Order has no rider to rate");
            }
            if (order.getStatus() != OrderStatus.DELIVERED) {
                throw new RuntimeException("You can rate rider only after order is delivered");
            }
            if (riderRatingRepository.findByOrderId(orderId).isPresent()) {
                throw new RuntimeException("Rating already submitted for this order");
            }

            RiderRatingEntity e = new RiderRatingEntity();
            e.setOrderId(orderId);
            e.setRiderId(order.getRiderId());
            e.setUserId(userId);
            e.setStars(stars);
            e.setComment(trimToNull(dto.getComment()));
            e.setComplimentsCsv(normalizeCompliments(dto.getCompliments()));
            riderRatingRepository.save(e);

            refreshRiderAverage(order.getRiderId());

            response.setData("Rating submitted");
            response.setMessage("Rating submitted");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ApiResponse<RiderRatingSummaryDTO> getRiderRatingSummary(Long riderId) {
        ApiResponse<RiderRatingSummaryDTO> response = new ApiResponse<>();
        try {
            if (riderId == null) {
                throw new RuntimeException("riderId is required");
            }
            List<RiderRatingEntity> ratings = riderRatingRepository.findByRiderIdOrderByCreatedAtDesc(riderId);
            long total = ratings.size();
            double avg = total == 0 ? 0.0 : ratings.stream().mapToInt(RiderRatingEntity::getStars).average().orElse(0.0);
            long positive = ratings.stream().filter(r -> r.getStars() >= 4).count();
            double positivePercent = total == 0 ? 0.0 : (positive * 100.0) / total;

            List<RiderRatingBreakdownItemDTO> breakdown = new ArrayList<>();
            for (int s = 5; s >= 1; s--) {
                final int starsBucket = s;
                long count = ratings.stream().filter(r -> r.getStars() == starsBucket).count();
                breakdown.add(new RiderRatingBreakdownItemDTO(starsBucket, count));
            }

            Map<String, Long> complimentCount = new HashMap<>();
            for (RiderRatingEntity r : ratings) {
                if (r.getComplimentsCsv() == null || r.getComplimentsCsv().isBlank()) {
                    continue;
                }
                for (String part : r.getComplimentsCsv().split(",")) {
                    String c = part.trim();
                    if (c.isEmpty()) {
                        continue;
                    }
                    complimentCount.merge(c, 1L, (a, b) -> Long.valueOf(a.longValue() + b.longValue()));
                }
            }
            List<RiderComplimentStatDTO> top = complimentCount.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                    .limit(8)
                    .map(e -> new RiderComplimentStatDTO(e.getKey(), e.getValue()))
                    .collect(Collectors.toList());

            RiderRatingSummaryDTO data = RiderRatingSummaryDTO.builder()
                    .riderId(riderId)
                    .averageRating(round2(avg))
                    .totalRatings(total)
                    .positivePercent(round2(positivePercent))
                    .breakdown(breakdown)
                    .topCompliments(top)
                    .build();

            response.setData(data);
            response.setMessage("OK");
            response.setMessageKey("SUCCESS");
            response.setSuccess(true);
            response.setStatus(200);
        } catch (Exception e) {
            response.setMessage(e.getMessage());
            response.setMessageKey("ERROR");
            response.setSuccess(false);
            response.setStatus(500);
        }
        return response;
    }

    private void refreshRiderAverage(Long riderId) {
        if (riderId == null) {
            return;
        }
        RiderEntity rider = riderRepository.findById(riderId).orElse(null);
        if (rider == null) {
            return;
        }
        List<RiderRatingEntity> ratings = riderRatingRepository.findByRiderIdOrderByCreatedAtDesc(riderId);
        double avg = ratings.isEmpty() ? 0.0 : ratings.stream().mapToInt(RiderRatingEntity::getStars).average().orElse(0.0);
        rider.setRating(round2(avg));
        riderRepository.save(rider);
    }

    private static String normalizeCompliments(List<String> compliments) {
        if (compliments == null || compliments.isEmpty()) {
            return null;
        }
        List<String> list = compliments.stream()
                .map(RiderRatingServiceImpl::toSlug)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
        return list.isEmpty() ? null : String.join(",", list);
    }

    private static String toSlug(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = t.replaceAll("[^A-Za-z0-9 ]", " ");
        t = t.trim().replaceAll("\\s+", "_").toUpperCase(Locale.ROOT);
        return t.isEmpty() ? null : t;
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}

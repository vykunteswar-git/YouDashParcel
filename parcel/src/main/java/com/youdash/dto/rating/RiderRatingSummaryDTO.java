package com.youdash.dto.rating;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RiderRatingSummaryDTO {
    private Long riderId;
    private Double averageRating;
    private Long totalRatings;
    private Double positivePercent;
    private List<RiderRatingBreakdownItemDTO> breakdown;
    private List<RiderComplimentStatDTO> topCompliments;
}

package com.youdash.dto.rating;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RiderRatingBreakdownItemDTO {
    private Integer stars;
    private Long count;
}

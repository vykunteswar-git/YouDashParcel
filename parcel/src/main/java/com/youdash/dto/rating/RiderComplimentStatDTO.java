package com.youdash.dto.rating;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RiderComplimentStatDTO {
    private String compliment;
    private Long count;
}

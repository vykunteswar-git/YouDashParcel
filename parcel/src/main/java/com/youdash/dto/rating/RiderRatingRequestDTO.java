package com.youdash.dto.rating;

import lombok.Data;

import java.util.List;

@Data
public class RiderRatingRequestDTO {
    private Integer stars;
    private List<String> compliments;
    private String comment;
}

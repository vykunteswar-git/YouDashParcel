package com.youdash.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class AdminOrdersPageResponseDTO {
    private List<OrderResponseDTO> orders;
    private int totalPages;
    private long totalElements;
    private int number;
    private int size;
}

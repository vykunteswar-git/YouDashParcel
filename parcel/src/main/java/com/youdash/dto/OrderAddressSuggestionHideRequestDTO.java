package com.youdash.dto;

import com.youdash.model.OrderAddressRole;
import lombok.Data;

@Data
public class OrderAddressSuggestionHideRequestDTO {
    private OrderAddressRole role;
    private Double lat;
    private Double lng;
}

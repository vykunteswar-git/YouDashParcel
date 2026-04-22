package com.youdash.dto;

import com.youdash.model.OrderAddressRole;
import lombok.Data;

@Data
public class OrderAddressSuggestionEditRequestDTO {
    private OrderAddressRole role;
    private Double lat;
    private Double lng;
    private String address;
    private String tag;
    private String doorNo;
    private String landmark;
    private String contactName;
    private String contactPhone;
}

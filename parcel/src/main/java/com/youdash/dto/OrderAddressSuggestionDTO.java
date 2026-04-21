package com.youdash.dto;

import com.youdash.model.OrderAddressRole;
import lombok.Builder;
import lombok.Data;

/**
 * Recent distinct locations from the user's orders for autofill on new bookings.
 * Map {@link #role} to pickup vs drop fields on {@link CreateOrderRequestDTO}; use
 * {@link #contactName} / {@link #contactPhone} for sender (pickup) or receiver (drop).
 */
@Data
@Builder
public class OrderAddressSuggestionDTO {

    private OrderAddressRole role;
    private Double lat;
    private Double lng;
    /** Sender name when {@link #role} is PICKUP; receiver name when DROP. */
    private String contactName;
    /** Sender phone when PICKUP; receiver phone when DROP. */
    private String contactPhone;
    /** Order {@code createdAt} for the most recent use of this coordinate (ISO-8601). */
    private String lastUsedAt;
}

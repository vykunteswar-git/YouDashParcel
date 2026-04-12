package com.youdash.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPromiseDTO {

    /** e.g. "Delivery by Tomorrow 6 PM" */
    private String message;

    /** e.g. "Pickup before 3 PM" — may be null for pure HOURS fallback */
    private String cutoffInfo;

    /** True when all today's cutoffs were missed and the next-day slot is used */
    private Boolean isNextDayShifted;
}

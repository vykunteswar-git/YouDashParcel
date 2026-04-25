package com.youdash.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryPromiseDTO {

    /** e.g. missed-slot notice or "Slot is available." */
    private String message;

    /** e.g. "Pickup before 3 PM" — may be null for pure HOURS fallback */
    private String cutoffInfo;

    /** Full delivery-by line when known (always serialized for clients to diff API versions). */
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String deliveredBy;

    /** True when all today's cutoffs were missed and the next-day slot is used */
    private Boolean isNextDayShifted;
}

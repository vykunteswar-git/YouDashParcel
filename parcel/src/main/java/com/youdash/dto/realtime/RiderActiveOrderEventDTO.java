package com.youdash.dto.realtime;

import lombok.Data;

/**
 * Pushed to {@code /topic/riders/{riderId}/active-order} while the rider has an INCITY assignment.
 * Clients should call {@code GET /orders/{id}} (or equivalent) to load full order details.
 */
@Data
public class RiderActiveOrderEventDTO {
    /**
     * Set only when {@link #event} is {@code snapshot}: whether this rider currently has an
     * active INCITY assignment. Incremental events may leave this null.
     */
    private Boolean hasActiveOrder;

    private Long orderId;
    /** Current {@link com.youdash.model.OrderStatus} name */
    private String status;
    /**
     * snapshot — sent once when subscribing to {@code /topic/riders/{id}/active-order} (includes {@link #hasActiveOrder});
     * assigned — rider accepted (COD → CONFIRMED or online → RIDER_ACCEPTED);
     * confirmed — online payment completed → CONFIRMED;
     * status_updated — pickup / transit;
     * reach_destination — rider at drop (still IN_TRANSIT);
     * otp_verified — delivery OTP ok;
     * delivered — handoff complete;
     * released — rider no longer assigned (cancel, payment timeout, etc.)
     */
    private String event;
    /** When {@code event} is {@code released}: e.g. USER_CANCELLED, PAYMENT_TIMEOUT */
    private String reason;
}

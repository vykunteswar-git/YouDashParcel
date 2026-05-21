package com.youdash.service.sms;

import lombok.Getter;

@Getter
public class SmsDeliveryException extends RuntimeException {

    public enum Reason {
        INVALID_MOBILE,
        SERVICE_UNAVAILABLE,
        INSUFFICIENT_BALANCE,
        DLT_REJECTED,
        PROVIDER_ERROR
    }

    private final Reason reason;

    public SmsDeliveryException(Reason reason, String userMessage) {
        super(userMessage);
        this.reason = reason;
    }

    public SmsDeliveryException(Reason reason, String userMessage, Throwable cause) {
        super(userMessage, cause);
        this.reason = reason;
    }
}

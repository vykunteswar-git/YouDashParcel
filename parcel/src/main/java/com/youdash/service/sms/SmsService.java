package com.youdash.service.sms;

/**
 * Delivers OTP via SMS (MSG91 when enabled).
 */
public interface SmsService {

    /**
     * Sends OTP to an Indian mobile in MSG91 format ({@code 91XXXXXXXXXX}).
     *
     * @param mobile91 mobile with country code, e.g. {@code 919876543210}
     * @param otp      numeric OTP stored in DB
     * @throws SmsDeliveryException on validation, provider, or transport errors
     */
    void sendOtp(String mobile91, String otp);
}

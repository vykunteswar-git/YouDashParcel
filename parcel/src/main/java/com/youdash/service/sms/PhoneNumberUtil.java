package com.youdash.service.sms;

/**
 * Normalizes Indian mobile numbers for DB storage (10 digits) and MSG91 ({@code 91 + 10 digits}).
 */
public final class PhoneNumberUtil {

    private static final java.util.regex.Pattern INDIAN_TEN_DIGIT =
            java.util.regex.Pattern.compile("^[6-9]\\d{9}$");

    private PhoneNumberUtil() {
    }

    /** Returns 10-digit national number for DB; throws if invalid. */
    public static String normalizeNational(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new SmsDeliveryException(SmsDeliveryException.Reason.INVALID_MOBILE, "Invalid mobile number");
        }
        String digits = raw.trim().replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (!INDIAN_TEN_DIGIT.matcher(digits).matches()) {
            throw new SmsDeliveryException(SmsDeliveryException.Reason.INVALID_MOBILE, "Invalid mobile number");
        }
        return digits;
    }

    /** Returns {@code 91XXXXXXXXXX} for MSG91. */
    public static String toMsg91Mobile(String nationalTenDigit) {
        return "91" + normalizeNational(nationalTenDigit);
    }
}
